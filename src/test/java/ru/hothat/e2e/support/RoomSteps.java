package ru.hothat.e2e.support;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Шаги, которыми сквозные тесты доводят комнату до нужного состояния.
 *
 * <p>Каждый шаг утверждает код ответа сам: сценарий, у которого не удалась
 * посадка, должен упасть на посадке, а не на первом же кадре тремя шагами
 * позже с невнятным «в кадре нет игрока».
 */
public final class RoomSteps {

    private final ApiClient api;

    public RoomSteps(ApiClient api) {
        this.api = api;
    }

    public String createRoom(Player host, String name, int capacity, String gameMode) {
        ApiClient.Response created = api.post("/api/v2/room", host.token(),
                Map.of("name", name, "capacity", capacity, "gameMode", gameMode));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        return created.text("/room/roomId");
    }

    public void enter(String roomId, Player guest) {
        ApiClient.Response entered = api.put("/api/v2/room/" + roomId + "/players/me", guest.token(), Map.of());
        assertThat(entered.status()).as("вход %s: %s", guest.nickname(), entered.raw()).isEqualTo(200);
    }

    public void watch(String roomId, Player spectator) {
        ApiClient.Response seated = api.put("/api/v2/room/" + roomId + "/spectators/me", spectator.token(), null);
        assertThat(seated.status()).as("место зрителя %s: %s", spectator.nickname(), seated.raw()).isEqualTo(200);
    }

    public String createTeam(String roomId, Player host, String name) {
        ApiClient.Response response = api.post("/api/v2/room/" + roomId + "/teams", host.token(),
                Map.of("name", name));
        assertThat(response.status()).as("команда %s: %s", name, response.raw()).isEqualTo(201);
        return response.text("/team/teamId");
    }

    public void joinTeam(String roomId, Player player, String teamId) {
        ApiClient.Response response = api.put(
                "/api/v2/room/" + roomId + "/teams/" + teamId + "/members/me", player.token(), null);
        assertThat(response.status()).as("посадка %s: %s", player.nickname(), response.raw()).isEqualTo(200);
    }

    public ApiClient.Response submitWords(String roomId, Player player, List<String> words) {
        ApiClient.Response submitted = api.put("/api/v2/game/" + roomId + "/word-submissions/me",
                player.token(), Map.of("words", words));
        assertThat(submitted.status()).as("слова %s: %s", player.nickname(), submitted.raw()).isEqualTo(200);
        return submitted;
    }

    /** Сообщение в чат; возвращает его идентификатор — он нужен правке и легенде слепков. */
    public String postChat(String roomId, Player author, String text) {
        ApiClient.Response posted = api.post("/api/v2/room/" + roomId + "/chat-messages", author.token(),
                Map.of("text", text));
        assertThat(posted.status()).as("сообщение %s: %s", author.nickname(), posted.raw()).isEqualTo(201);
        return posted.text("/message/messageId");
    }
}
