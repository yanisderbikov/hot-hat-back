package ru.hothat.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.e2e.support.ApiClient;
import ru.hothat.e2e.support.Channel;
import ru.hothat.e2e.support.E2ETest;
import ru.hothat.e2e.support.Frame;
import ru.hothat.e2e.support.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Каналы обязаны быть живыми: тот, кто уже смотрит, видит изменение без
 * перезагрузки.
 *
 * <p>Ровно этого не было. Сценарии {@code /api/v2} писали через {@code SaverRoom},
 * а событий шины не публиковали, и канал комнаты отдавал подписчику
 * приветственный кадр и молчал: игрок А не видел, как вошёл игрок Б, пока не
 * обновлял страницу. Теперь {@code RoomManager} публикует в шину после каждой
 * записи строк комнаты, а лобби слушает ту же шину — здесь проверяется, что
 * событие доходит до обоих именованных каналов.
 *
 * <p>Тест про живость, а не про содержимое: что именно несёт кадр канала
 * комнаты, проверяет {@link RoomChannelFrameE2ETest}; здесь — только что он
 * приходит и что в нём уже есть вошедший.
 */
class RoomRealtimeE2ETest extends E2ETest {

    @Test
    @DisplayName("Вход третьего игрока приходит второму кадром канала комнаты")
    void joinReachesRoomSubscriber() throws Exception {
        Player anya = registerArmed("Anya");
        Player boris = registerArmed("Boris");
        Player vera = registerArmed("Vera");

        ApiClient.Response created = api.post("/api/v2/room", anya.token(),
                Map.of("name", "Живая комната", "capacity", 4));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        String roomId = created.text("/room/roomId");

        ApiClient.Response borisIn = api.put("/api/v2/room/" + roomId + "/players/me", boris.token(), Map.of());
        assertThat(borisIn.status()).as(borisIn.raw()).isEqualTo(200);

        try (Channel room = Channel.open(ws("/ws/v2/room/" + roomId, boris))) {
            Frame welcome = room.nextFrame("приветственный кадр канала комнаты");
            assertThat(welcome.type()).isEqualTo("hello");
            assertThat(uids(welcome.at("/room/snapshot/players")))
                    .as("до входа Веры за столом двое")
                    .containsExactly(anya.uid(), boris.uid());

            ApiClient.Response veraIn = api.put("/api/v2/room/" + roomId + "/players/me", vera.token(), Map.of());
            assertThat(veraIn.status()).as(veraIn.raw()).isEqualTo(200);

            // Вход даёт два события подряд — место и счётчик живых, — поэтому
            // ждём кадр по условию, а не первый попавшийся.
            Frame update = room.awaitFrame("кадр после входа Веры",
                    frame -> uids(frame.at("/room/snapshot/players")).contains(vera.uid()));
            assertThat(update.type()).isEqualTo("room");
        }
    }

    @Test
    @DisplayName("Витрина лобби узнаёт о новой комнате без перезагрузки")
    void lobbyLearnsAboutNewRoom() throws Exception {
        Player watcher = registerArmed("Watcher");
        Player host = registerArmed("Host");

        try (Channel lobby = Channel.open(ws("/ws/v2/lobby", watcher))) {
            assertThat(lobby.next()).as("приветственный кадр лобби").isNotBlank();

            ApiClient.Response created = api.post("/api/v2/room", host.token(),
                    Map.of("name", "Новая на витрине", "capacity", 4));
            assertThat(created.status()).as(created.raw()).isEqualTo(201);

            assertThat(lobby.next())
                    .as("лобби должно прислать кадр после появления комнаты")
                    .isNotNull();
        }
    }

    private static List<String> uids(JsonNode seats) {
        List<String> uids = new ArrayList<>();
        seats.forEach(seat -> uids.add(seat.path("uid").asText()));
        return uids;
    }
}
