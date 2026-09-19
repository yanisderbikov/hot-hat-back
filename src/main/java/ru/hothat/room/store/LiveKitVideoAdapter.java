package ru.hothat.room.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.hothat.common.livekit.LiveKitClient;
import ru.hothat.room.port.VideoSessionPort;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Видеосвязь глазами комнаты: реализация {@link VideoSessionPort}.
 *
 * <p>Здесь нет ни одного решения о допуске — только подпись. Кого пускать,
 * решено раньше, в сценарии; сюда приезжает уже отобранный участник. Из-за
 * этого класс и лежит в {@code store}: он знает чужой протокол и больше
 * ничего, ровно как {@code LiveKitPresenceAdapter} в области партии.
 *
 * <p>Срок жизни токена — два часа, как и был. Он длиннее партии намеренно:
 * переподключение вкладки не должно упираться в протухший токен посреди хода.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveKitVideoAdapter implements VideoSessionPort {

    /** Столько живёт токен участника; переподключение вкладки в него укладывается. */
    private static final long TOKEN_TTL_SECONDS = 7200;

    private final LiveKitClient liveKit;

    @Value("${livekit.url:}")
    private String livekitUrl;

    @Value("${turn.urls:}")
    private String turnUrls;

    @Value("${turn.secret:}")
    private String turnSecret;

    @Value("${turn.ttl-seconds:7200}")
    private long turnTtlSeconds;

    @Override
    public String serverUrl() {
        return livekitUrl == null ? "" : livekitUrl;
    }

    @Override
    public String participantToken(Participant participant) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("roomId", participant.roomId());
        metadata.put("teamId", participant.teamId());
        metadata.put("role", participant.role());
        metadata.put("isTestBot", participant.testBot());
        metadata.put("gameNumber", participant.gameNumber());
        return liveKit.accessToken(new LiveKitClient.AccessTokenRequest(
                participant.identity(), participant.name(), participant.roomId(), metadata,
                participant.publisher(), true, true, false, false, false, false, true,
                TOKEN_TTL_SECONDS));
    }

    /**
     * Учётка по схеме coturn: имя — «срок:идентификатор», пароль — его
     * HMAC-SHA1 на общем секрете. Секрета нет или адресов нет — учётки нет.
     */
    @Override
    public Optional<TurnTicket> turnTicket(String identity) {
        List<String> urls = Arrays.stream((turnUrls == null ? "" : turnUrls).split(","))
                .map(String::trim)
                .filter(value -> value.toLowerCase().startsWith("turn:")
                        || value.toLowerCase().startsWith("turns:"))
                .toList();
        if (turnSecret == null || turnSecret.isBlank() || urls.isEmpty()) {
            return Optional.empty();
        }
        long ttlSeconds = Math.min(21600, Math.max(600, turnTtlSeconds));
        long expiresAt = System.currentTimeMillis() / 1000 + ttlSeconds;
        String username = expiresAt + ":" + safeIdentity(identity);
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(turnSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            String credential = java.util.Base64.getEncoder()
                    .encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
            return Optional.of(new TurnTicket(urls, username, credential, ttlSeconds, expiresAt));
        } catch (Exception e) {
            // Без учётки связь ещё возможна напрямую, а отказ выключил бы видео
            // и тем, у кого ретранслятор не нужен вовсе.
            log.warn("Не удалось подписать TURN-учётку: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** В имени учётки допустимы только незарезервированные символы URI. */
    private static String safeIdentity(String identity) {
        String safe = (identity == null || identity.isBlank() ? "player" : identity)
                .replaceAll("[^A-Za-z0-9._~-]", "_");
        safe = safe.length() > 64 ? safe.substring(0, 64) : safe;
        return safe.isEmpty() ? "player" : safe;
    }
}
