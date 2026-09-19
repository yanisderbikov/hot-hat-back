package ru.hothat.common.livekit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.hothat.config.ApiException;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Клиент LiveKit: подпись токенов и twirp-вызовы RoomService и Egress.
 *
 * <p>SDK для Java у LiveKit нет, поэтому здесь прямая работа с тем же
 * протоколом, что и у livekit-server-sdk для Node.
 *
 * <p>Клиент лежит в общем месте, а не в области: разговаривать с LiveKit
 * приходится четверым — комнате (пропуск в видео), партии (присутствие и
 * рассылка диверсий), записи (Egress) и консоли администратора (выставить
 * участника, погасить комнату). Каждая из четырёх держит поверх него СВОЙ
 * переходник в {@code store} и знает только свой порт; общая здесь одна
 * подпись протокола, а не решения о допуске.
 */
@Slf4j
@Component
public class LiveKitClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final String livekitUrl;
    private final String apiKey;
    private final String apiSecret;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LiveKitClient(@Value("${livekit.url:}") String livekitUrl,
                         @Value("${livekit.api-key:}") String apiKey,
                         @Value("${livekit.api-secret:}") String apiSecret,
                         WebClient.Builder builder) {
        this.livekitUrl = livekitUrl == null ? "" : livekitUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
        this.webClient = builder.build();
    }

    private void requireConfigured() {
        if (livekitUrl.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            throw ApiException.of("LIVEKIT_NOT_CONFIGURED", 503);
        }
    }

    /** wss://... → https://...: twirp ходит по HTTP на тот же хост. */
    private String httpUrl() {
        requireConfigured();
        String url = livekitUrl.replaceFirst("(?i)^wss:", "https:").replaceFirst("(?i)^ws:", "http:");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Имя комнаты в LiveKit: hot-hat-&lt;roomId&gt;. */
    public String roomName(String roomId) {
        return "hot-hat-" + roomId;
    }

    /** Токен участника комнаты: игрока, зрителя или скрытого рекордера. */
    public String accessToken(AccessTokenRequest request) {
        requireConfigured();
        Map<String, Object> video = new HashMap<>();
        if (request.roomJoin()) {
            video.put("roomJoin", true);
            video.put("room", roomName(request.roomId()));
        }
        video.put("canPublish", request.canPublish());
        video.put("canSubscribe", request.canSubscribe());
        video.put("canPublishData", request.canPublishData());
        if (request.hidden()) {
            video.put("hidden", true);
        }
        if (request.recorder()) {
            video.put("recorder", true);
        }
        if (request.roomRecord()) {
            video.put("roomRecord", true);
        }
        if (request.roomAdmin()) {
            video.put("roomAdmin", true);
        }

        long now = System.currentTimeMillis();
        long ttl = request.ttlSeconds() > 0 ? request.ttlSeconds() : 7200;
        var builder = Jwts.builder()
                .issuer(apiKey)
                .subject(request.identity())
                .issuedAt(new Date(now))
                .notBefore(new Date(now))
                .expiration(new Date(now + ttl * 1000))
                .claim("video", video);
        if (request.name() != null && !request.name().isBlank()) {
            builder.claim("name", request.name());
        }
        if (request.metadata() != null && !request.metadata().isEmpty()) {
            builder.claim("metadata", writeJson(request.metadata()));
        }
        return builder.signWith(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")).compact();
    }

    /** Служебный токен для twirp: только права администратора комнаты. */
    private String serviceToken(boolean record) {
        return accessToken(new AccessTokenRequest("hot-hat-service", null, "", null,
                false, false, false, false, false, record, true, false, 600));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> twirp(String service, String method, Map<String, Object> body, boolean record) {
        String url = httpUrl() + "/twirp/" + service + "/" + method;
        try {
            String response = webClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + serviceToken(record))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body == null ? Map.of() : body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TIMEOUT);
            if (response == null || response.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(response, Map.class);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            String details = e.getResponseBodyAsString();
            log.warn("LiveKit {}.{} вернул {}: {}", service, method, e.getStatusCode(), details);
            throw ApiException.of(extractLiveKitError(details, service, method, e.getStatusCode().value()),
                    e.getStatusCode().is5xxServerError() ? 502 : e.getStatusCode().value());
        } catch (Exception e) {
            log.warn("LiveKit {}.{} недоступен: {}", service, method, e.getMessage());
            throw ApiException.of("LIVEKIT_UNAVAILABLE", 502);
        }
    }

    private String extractLiveKitError(String body, String service, String method, int status) {
        try {
            Map<?, ?> parsed = objectMapper.readValue(body, Map.class);
            Object message = parsed.get("msg") != null ? parsed.get("msg") : parsed.get("message");
            if (message != null) {
                return String.valueOf(message);
            }
        } catch (Exception ignored) {
            // Тело ответа не JSON — отдаём обобщённый код.
        }
        return "LIVEKIT_" + service.replace("livekit.", "").toUpperCase() + "_" + method.toUpperCase() + "_" + status;
    }

    @SuppressWarnings("unchecked")
    public List<String> listParticipantIdentities(String roomId) {
        Map<String, Object> response = twirp("livekit.RoomService", "ListParticipants",
                Map.of("room", roomName(roomId)), false);
        List<String> identities = new ArrayList<>();
        Object participants = response.get("participants");
        if (participants instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> participant) {
                    Object identity = participant.get("identity");
                    if (identity != null && !String.valueOf(identity).isEmpty()) {
                        identities.add(String.valueOf(identity));
                    }
                }
            }
        }
        return identities;
    }

    public void removeParticipant(String roomId, String identity) {
        twirp("livekit.RoomService", "RemoveParticipant",
                Map.of("room", roomName(roomId), "identity", identity), false);
    }

    public void deleteRoom(String roomId) {
        twirp("livekit.RoomService", "DeleteRoom", Map.of("room", roomName(roomId)), false);
    }

    /**
     * Диверсия уходит в комнату надёжным data-пакетом (kind=0). Запись события
     * в базе остаётся фолбэком для тех, кто переподключился и пропустил пакет.
     *
     * @param audience кому доставить; пустой список — всем участникам комнаты.
     *                 Идентичность участника LiveKit — его {@code uid}, так что
     *                 список адресатов — это список uid.
     */
    public void sendSabotage(String roomId, Map<String, Object> event, List<String> audience) {
        String payload = writeJson(Map.of("hotHatEvent", "sabotage", "event", event));
        Map<String, Object> body = new HashMap<>();
        body.put("room", roomName(roomId));
        body.put("data", Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8)));
        body.put("kind", "RELIABLE");
        body.put("topic", "hot-hat-sabotage");
        if (audience != null && !audience.isEmpty()) {
            body.put("destination_identities", List.copyOf(audience));
        }
        twirp("livekit.RoomService", "SendData", body, false);
    }

    /** twirp-вызов livekit.Egress; им пользуется запись партий. */
    public Map<String, Object> egress(String method, Map<String, Object> body) {
        return twirp("livekit.Egress", method, body, true);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось сериализовать полезную нагрузку LiveKit", e);
        }
    }

    /**
     * Права участника, за которого просят токен.
     *
     * <p>Девять признаков — это девять флагов протокола LiveKit, а не наши
     * роли: кого пускать и с чем, решают области, и здесь решения нет.
     */
    public record AccessTokenRequest(String identity,
                                     String name,
                                     String roomId,
                                     Map<String, Object> metadata,
                                     boolean canPublish,
                                     boolean canSubscribe,
                                     boolean canPublishData,
                                     boolean hidden,
                                     boolean recorder,
                                     boolean roomRecord,
                                     boolean roomAdmin,
                                     boolean roomJoin,
                                     long ttlSeconds) {
    }
}
