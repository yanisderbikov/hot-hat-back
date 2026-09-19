package ru.hothat.e2e.support;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Дружба двоих: заявка по нику и ответ на неё.
 *
 * <p>Вынесена отдельно, потому что нужна двум разным проверкам — переписке
 * (без дружбы чат отвечает 403 FRIEND_REQUIRED) и списку друзей.
 */
public final class Friendship {

    private Friendship() {
    }

    public static void make(ApiClient api, Player from, Player to) {
        ApiClient.Response sent = api.post("/api/v2/friends/requests/by-nickname", from.token(),
                Map.of("nickname", to.nickname()));
        assertThat(sent.status()).as("заявка в друзья: %s", sent.raw()).isEqualTo(201);

        ApiClient.Response incoming = api.get("/api/v2/friends/requests/incoming", to.token());
        assertThat(incoming.status()).as(incoming.raw()).isEqualTo(200);
        assertThat(incoming.at("/items")).as("входящая заявка не доехала: %s", incoming.raw()).hasSize(1);
        long requestId = incoming.at("/items/0/id").asLong();

        ApiClient.Response accepted = api.post(
                "/api/v2/friends/requests/" + requestId + "/acceptance", to.token(), null);
        assertThat(accepted.status()).as("принятие заявки: %s", accepted.raw()).isEqualTo(200);
    }
}
