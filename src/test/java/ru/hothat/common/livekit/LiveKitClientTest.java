package ru.hothat.common.livekit;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import ru.hothat.config.ApiException;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Пропуск в видеосвязь: что именно подписано в токене участника.
 *
 * <p>Проверяется поведение, а не устройство: токен разбирается тем же
 * секретом, которым подписан, и смотрится, что в нём разрешено. Сеть не
 * нужна — подпись считается на месте.
 *
 * <p>Проверять это важно потому, что права участника — единственное, что
 * отделяет зрителя от игрока внутри видеокомнаты: сервер видеосвязи верит
 * токену и больше ничего не спрашивает.
 */
class LiveKitClientTest {

    private static final String SECRET = "секрет-длиной-побольше-тридцати-двух-байт";

    private static final LiveKitClient CLIENT =
            new LiveKitClient("wss://livekit.example", "ключ", SECRET, WebClient.builder());

    private static LiveKitClient.AccessTokenRequest player(String roomId, boolean canPublish) {
        return new LiveKitClient.AccessTokenRequest("uid-ann", "Аня", roomId,
                Map.of("role", "player", "teamId", "team-1"),
                canPublish, true, true, false, false, false, false, true, 60);
    }

    private static Claims claims(String token) {
        return Jwts.parser()
                .verifyWith(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Test
    @DisplayName("Имя комнаты в LiveKit получается из идентификатора комнаты с общим префиксом")
    void roomNameIsPrefixed() {
        assertThat(CLIENT.roomName("hat-3f1c")).isEqualTo("hot-hat-hat-3f1c");
    }

    @Test
    @DisplayName("Токен подписан секретом сервера и называет того, кому выдан")
    void tokenIsSignedAndNamesItsHolder() {
        Claims claims = claims(CLIENT.accessToken(player("hat-3f1c", true)));

        assertThat(claims.getSubject()).isEqualTo("uid-ann");
        assertThat(claims.getIssuer()).isEqualTo("ключ");
        assertThat(claims.get("name")).isEqualTo("Аня");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    @DisplayName("Токен пускает только в свою комнату")
    void tokenGrantsOnlyItsOwnRoom() {
        Claims claims = claims(CLIENT.accessToken(player("hat-3f1c", true)));

        assertThat(video(claims)).containsEntry("room", "hot-hat-hat-3f1c").containsEntry("roomJoin", true);
    }

    @Test
    @DisplayName("Зритель не получает права показывать себя")
    void spectatorMayNotPublish() {
        Claims claims = claims(CLIENT.accessToken(player("hat-3f1c", false)));

        assertThat(video(claims)).containsEntry("canPublish", false).containsEntry("canSubscribe", true);
    }

    @Test
    @DisplayName("Прав, о которых не просили, в токене нет вовсе: ни записи, ни скрытности, ни админства")
    void unaskedPowersAreAbsent() {
        Claims claims = claims(CLIENT.accessToken(player("hat-3f1c", true)));

        assertThat(video(claims))
                .doesNotContainKeys("hidden", "recorder", "roomRecord", "roomAdmin");
    }

    @Test
    @DisplayName("Метаданные едут строкой JSON: их читает браузер каждого участника")
    void metadataTravelsAsJsonText() {
        Claims claims = claims(CLIENT.accessToken(player("hat-3f1c", true)));

        assertThat(claims.get("metadata", String.class)).contains("\"role\":\"player\"");
    }

    @Test
    @DisplayName("Без настроенного сервера видеосвязи токен не подписывается, а отказывает")
    void unconfiguredServerRefuses() {
        LiveKitClient unconfigured = new LiveKitClient("", "", "", WebClient.builder());

        assertThatThrownBy(() -> unconfigured.accessToken(player("hat-3f1c", true)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("LIVEKIT_NOT_CONFIGURED");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> video(Claims claims) {
        return claims.get("video", Map.class);
    }
}
