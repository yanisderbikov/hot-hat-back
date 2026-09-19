package ru.hothat.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.e2e.support.ApiClient;
import ru.hothat.e2e.support.E2ETest;
import ru.hothat.e2e.support.Friendship;
import ru.hothat.e2e.support.Player;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Счётчик непрочитанного в переписке.
 *
 * <p>Чинилось: значок на кнопке чата жил своей жизнью — рос от собственных
 * сообщений и не обнулялся отметкой прочтения. Поэтому здесь проверяется не
 * только «вырос и обнулился», но и то, что у отправителя он не растёт вовсе, и
 * что общий счётчик совпадает с суммой по перепискам.
 */
class ChatUnreadE2ETest extends E2ETest {

    @Test
    @DisplayName("Непрочитанное растёт у получателя, не растёт у отправителя и обнуляется отметкой")
    void unreadGrowsAndResets() {
        Player anya = register("Anya");
        Player boris = register("Boris");
        Friendship.make(api, anya, boris);

        send(anya, boris, "Привет");
        send(anya, boris, "Ты тут?");

        assertThat(unreadWith(boris, anya.uid())).as("у получателя два непрочитанных").isEqualTo(2);
        assertThat(totalUnread(boris)).isEqualTo(2);
        assertThat(unreadWith(anya, boris.uid())).as("свои сообщения непрочитанными не считаются").isZero();
        assertThat(totalUnread(anya)).isZero();

        ApiClient.Response marked = api.put("/api/v2/chat/" + anya.uid() + "/read-mark", boris.token(), null);
        assertThat(marked.status()).as(marked.raw()).isEqualTo(204);

        assertThat(unreadWith(boris, anya.uid())).as("отметка обнуляет переписку").isZero();
        assertThat(totalUnread(boris)).as("отметка обнуляет и общий счётчик").isZero();

        // Следующее сообщение снова считается: отметка гасит прошлое, а не всё будущее.
        send(anya, boris, "Ау");
        assertThat(unreadWith(boris, anya.uid())).isEqualTo(1);
        assertThat(totalUnread(boris)).isEqualTo(1);
    }

    @Test
    @DisplayName("Постороннему написать нельзя: переписка только между друзьями")
    void strangersCannotWrite() {
        // Обратная сторона: без этого правила счётчик непрочитанного растил бы
        // кто угодно.
        Player anya = register("Anya");
        Player stranger = register("Stranger");

        ApiClient.Response refused = api.post("/api/v2/chat/" + stranger.uid() + "/messages", anya.token(),
                Map.of("text", "Привет"));

        assertThat(refused.status()).isEqualTo(403);
        assertThat(refused.raw()).contains("FRIEND_REQUIRED");
    }

    private void send(Player from, Player to, String text) {
        ApiClient.Response sent = api.post("/api/v2/chat/" + to.uid() + "/messages", from.token(),
                Map.of("text", text));
        assertThat(sent.status()).as("отправка «%s»: %s", text, sent.raw()).isEqualTo(201);
    }

    private int unreadWith(Player viewer, String peerUid) {
        ApiClient.Response threads = api.get("/api/v2/chat/threads", viewer.token());
        assertThat(threads.status()).as(threads.raw()).isEqualTo(200);
        for (JsonNode thread : threads.at("/items")) {
            if (peerUid.equals(thread.get("peerUid").asText())) {
                return thread.get("unreadCount").asInt();
            }
        }
        throw new AssertionError("переписки с " + peerUid + " нет: " + threads.raw());
    }

    private int totalUnread(Player viewer) {
        return api.get("/api/v2/chat/threads", viewer.token()).number("/totalUnread");
    }
}
