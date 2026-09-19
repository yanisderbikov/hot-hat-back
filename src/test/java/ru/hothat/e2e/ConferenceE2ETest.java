package ru.hothat.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.e2e.support.ApiClient;
import ru.hothat.e2e.support.Channel;
import ru.hothat.e2e.support.E2ETest;
import ru.hothat.e2e.support.Frame;
import ru.hothat.e2e.support.Friendship;
import ru.hothat.e2e.support.Player;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Видео-чат от создания до игровой комнаты — тем же путём, каким его пройдёт
 * страница: HTTP на запись, каналы {@code /ws/v2/me/social} и
 * {@code /ws/v2/conference/{id}} на чтение.
 *
 * <p>Проверяется то, ради чего возможность переехала с Vercel и Firestore:
 * приглашение доезжает до адресата кадром, а не опросом; состав и лента
 * приходят участникам одним кадром; выгнанный теряет канал сразу.
 */
class ConferenceE2ETest extends E2ETest {

    @Test
    @DisplayName("Создать, позвать друга, вступить, написать, завести комнату, выгнать")
    void conferenceLifecycle() throws Exception {
        Player anya = registerArmed("Anya");
        Player boris = register("Boris");
        Player vera = register("Vera");
        Player stranger = register("Stranger");
        Friendship.make(api, anya, boris);
        Friendship.make(api, anya, vera);

        ApiClient.Response created = api.post("/api/v2/conference", anya.token(), Map.of());
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        String conferenceId = created.text("/conference/conferenceId");
        assertThat(conferenceId).matches("^vc-[a-f0-9]{16}$");
        assertThat(created.text("/conference/hostUid")).isEqualTo(anya.uid());
        assertThat(uids(created.at("/conference/participants"))).containsExactly(anya.uid());
        assertThat(created.at("/conference/gameRoom").isNull()).isTrue();

        // Посторонний и ещё не вступивший приглашённый видео-чат не видят.
        ApiClient.Response strangerLook = api.get("/api/v2/conference/" + conferenceId, stranger.token());
        assertThat(strangerLook.status()).isEqualTo(403);
        assertThat(strangerLook.raw()).contains("CONFERENCE_INVITE_REQUIRED");
        ApiClient.Response strangerInvite = api.post("/api/v2/conference/" + conferenceId + "/invites",
                anya.token(), Map.of("friendUid", stranger.uid()));
        assertThat(strangerInvite.status()).as("звать можно только друга").isEqualTo(403);
        assertThat(strangerInvite.raw()).contains("FRIEND_REQUIRED");

        try (Channel borisSocial = Channel.open(ws("/ws/v2/me/social", boris));
             Channel anyaConference = Channel.open(ws("/ws/v2/conference/" + conferenceId, anya))) {
            Frame socialHello = borisSocial.nextFrame("приветственный кадр социального канала Бориса");
            assertThat(socialHello.type()).isEqualTo("hello");
            assertThat(socialHello.at("/social/conferenceInvites")).isEmpty();

            Frame conferenceHello = anyaConference.nextFrame("приветственный кадр канала видео-чата");
            assertThat(conferenceHello.type()).isEqualTo("hello");
            assertThat(conferenceHello.at("/conference/messages")).isEmpty();
            assertThat(conferenceHello.number("/conference/limit")).isEqualTo(120);

            // Приглашение: карточка приезжает Борису кадром социального канала.
            ApiClient.Response invited = api.post("/api/v2/conference/" + conferenceId + "/invites",
                    anya.token(), Map.of("friendUid", boris.uid()));
            assertThat(invited.status()).as(invited.raw()).isEqualTo(201);
            assertThat(invited.text("/outcome")).isEqualTo("invited");
            assertThat(uids(invited.at("/conference/invited"))).containsExactly(boris.uid());

            Frame invitedFrame = borisSocial.awaitFrame("кадр с приглашением в видео-чат",
                    frame -> !frame.at("/social/conferenceInvites").isEmpty());
            JsonNode card = invitedFrame.at("/social/conferenceInvites").get(0);
            assertThat(card.get("conferenceId").asText()).isEqualTo(conferenceId);
            assertThat(card.get("inviterUid").asText()).isEqualTo(anya.uid());
            assertThat(card.get("inviterNickname").asText()).isEqualTo("Anya");

            ApiClient.Response repeated = api.post("/api/v2/conference/" + conferenceId + "/invites",
                    anya.token(), Map.of("friendUid", boris.uid()));
            assertThat(repeated.text("/outcome")).as("второго приглашения нет").isEqualTo("already_pending");
            assertThat(api.get("/api/v2/conference/" + conferenceId, boris.token()).status())
                    .as("приглашённый до ответа — не участник").isEqualTo(403);

            // Вступление: и у Бориса карточка гаснет, и у Ани в кадре двое.
            ApiClient.Response accepted = api.post(
                    "/api/v2/conference/" + conferenceId + "/invites/me/acceptance", boris.token(), Map.of());
            assertThat(accepted.status()).as(accepted.raw()).isEqualTo(200);
            assertThat(accepted.at("/accepted").asBoolean()).isTrue();
            assertThat(uids(accepted.at("/conference/participants"))).containsExactly(anya.uid(), boris.uid());
            borisSocial.awaitFrame("кадр без приглашения после вступления",
                    frame -> frame.at("/social/conferenceInvites").isEmpty());
            anyaConference.awaitFrame("кадр видео-чата с двумя участниками",
                    frame -> frame.at("/conference/conference/participants").size() == 2);

            // Чат: сообщение Бориса приезжает Ане кадром той же формы, что и история.
            ApiClient.Response posted = api.post("/api/v2/conference/" + conferenceId + "/messages",
                    boris.token(), Map.of("text", "  Погнали!  "));
            assertThat(posted.status()).as(posted.raw()).isEqualTo(201);
            assertThat(posted.text("/message/text")).isEqualTo("Погнали!");
            assertThat(posted.text("/message/fromNickname")).isEqualTo("Boris");
            Frame withMessage = anyaConference.awaitFrame("кадр с сообщением Бориса",
                    frame -> frame.at("/conference/messages").size() == 1);
            assertThat(withMessage.text("/conference/messages/0/text")).isEqualTo("Погнали!");
            assertThat(withMessage.text("/conference/messages/0/fromUid")).isEqualTo(boris.uid());
            ApiClient.Response history = api.get("/api/v2/conference/" + conferenceId + "/messages", boris.token());
            assertThat(history.at("/items")).hasSize(1);
            assertThat(history.at("/items/0")).isEqualTo(withMessage.at("/conference/messages/0"));

            ApiClient.Response foreignFile = api.post("/api/v2/conference/" + conferenceId + "/file-messages",
                    boris.token(), Map.of("storageKey", "conference/" + conferenceId + "/" + anya.uid()
                                    + "/1788600000000-9f31ab77c204-x.png",
                            "name", "x.png", "contentType", "image/png", "sizeBytes", 10));
            assertThat(foreignFile.status()).as("файл из чужой папки не принимается").isEqualTo(403);
            assertThat(foreignFile.raw()).contains("CONFERENCE_FILE_INVALID");

            // Отклонение: карточка гаснет, второй ответ на то же приглашение невозможен.
            api.post("/api/v2/conference/" + conferenceId + "/invites", anya.token(), Map.of("friendUid", vera.uid()));
            ApiClient.Response declined = api.post(
                    "/api/v2/conference/" + conferenceId + "/invites/me/rejection", vera.token(), Map.of());
            assertThat(declined.status()).as(declined.raw()).isEqualTo(200);
            assertThat(declined.at("/accepted").asBoolean()).isFalse();
            assertThat(declined.at("/conference").isNull()).as("отклонивший состава не видит").isTrue();
            assertThat(api.post("/api/v2/conference/" + conferenceId + "/invites/me/acceptance",
                    vera.token(), Map.of()).status()).isEqualTo(404);

            // Комната этим составом: приватная, хозяин за столом, отметка о ней в кадре у всех.
            ApiClient.Response gameRoom = api.post("/api/v2/conference/" + conferenceId + "/game-room",
                    anya.token(), Map.of("participantUids", List.of(boris.uid(), vera.uid(), stranger.uid())));
            assertThat(gameRoom.status()).as(gameRoom.raw()).isEqualTo(201);
            String roomId = gameRoom.text("/conference/gameRoom/roomId");
            assertThat(roomId).matches("^hat-[a-f0-9]{16}$");
            assertThat(uids(gameRoom.at("/conference/gameRoom/memberUids")))
                    .as("место обещано только участникам звонка").containsExactly(anya.uid(), boris.uid());
            ApiClient.Response room = api.get("/api/v2/room/" + roomId, anya.token());
            assertThat(room.status()).as(room.raw()).isEqualTo(200);
            assertThat(room.at("/room/privateRoom").asBoolean()).isTrue();
            assertThat(room.text("/room/hostUid")).isEqualTo(anya.uid());
            assertThat(api.post("/api/v2/conference/" + conferenceId + "/game-room", anya.token(), Map.of())
                    .text("/conference/gameRoom/roomId")).as("пока комната набирается — она же").isEqualTo(roomId);
            anyaConference.awaitFrame("кадр с заведённой комнатой",
                    frame -> roomId.equals(frame.body().at("/conference/conference/gameRoom/roomId").asText(null)));

            // Выгон: Борис теряет канал сразу, а не при следующем открытии вкладки.
            try (Channel borisConference = Channel.open(ws("/ws/v2/conference/" + conferenceId, boris))) {
                assertThat(borisConference.nextFrame("приветственный кадр Бориса").type()).isEqualTo("hello");
                assertThat(api.post("/api/v2/conference/" + conferenceId + "/game-room", boris.token(), Map.of())
                        .status()).as("комнату заводит только хозяин").isEqualTo(403);
                ApiClient.Response kicked = api.send("DELETE",
                        "/api/v2/conference/" + conferenceId + "/members/" + boris.uid(), anya.token(), null, Map.of());
                assertThat(kicked.status()).as(kicked.raw()).isEqualTo(200);
                assertThat(uids(kicked.at("/conference/participants"))).containsExactly(anya.uid());
                Frame refused = borisConference.awaitFrame("отказ канала выгнанному",
                        frame -> "error".equals(frame.type()));
                assertThat(refused.text("/code")).isEqualTo("CONFERENCE_INVITE_REQUIRED");
            }
            assertThat(api.get("/api/v2/conference/" + conferenceId, boris.token()).status()).isEqualTo(403);
            assertThat(api.send("DELETE", "/api/v2/conference/" + conferenceId + "/members/" + anya.uid(),
                    anya.token(), null, Map.of()).raw()).contains("CONFERENCE_HOST_PROTECTED");
        }
    }

    @Test
    @DisplayName("Видеотокен выдаётся участнику и только ему; без видеоузла — честный 503")
    void videoTokenIsForParticipantsOnly() {
        Player anya = register("Anya");
        Player stranger = register("Stranger");
        String conferenceId = api.post("/api/v2/conference", anya.token(), Map.of()).text("/conference/conferenceId");

        ApiClient.Response foreign = api.post("/api/v2/conference/" + conferenceId + "/video-token",
                stranger.token(), Map.of());
        assertThat(foreign.status()).isEqualTo(403);

        // В сквозном контуре видеоузел не настроен: отказ должен быть объяснён,
        // а не превратиться в 500 на подписи токена.
        ApiClient.Response own = api.post("/api/v2/conference/" + conferenceId + "/video-token",
                anya.token(), Map.of());
        assertThat(own.status()).as(own.raw()).isEqualTo(503);
        assertThat(own.raw()).contains("LIVEKIT_NOT_CONFIGURED");
    }

    private static List<String> uids(JsonNode list) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        for (JsonNode item : list) {
            result.add(item.isTextual() ? item.asText() : item.get("uid").asText());
        }
        return result;
    }
}
