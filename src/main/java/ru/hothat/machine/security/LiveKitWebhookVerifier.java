package ru.hothat.machine.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * Проверка подписи вебхука LiveKit.
 *
 * <p>Сегодня она не делается вовсе: {@code RecordingServiceImpl:234} кладёт в
 * заявку на запись {@code signing_key}, то есть просит LiveKit подписывать
 * уведомления, а {@code RecordingStateController:44} сверяет вместо этого наш
 * собственный HMAC из строки запроса. Подпись самого LiveKit не читается
 * никогда — это прямо записано в аудите.
 *
 * <p>Почему для этого актора нельзя было обойтись общим заголовком с секретом,
 * как у cron и агента: конфигурация вебхука LiveKit — это пара
 * {@code {url, signing_key}}, произвольных заголовков она не отправляет.
 * Единственное удостоверение, которое LiveKit кладёт в заголовок сам, — вот
 * этот токен в {@code Authorization}. Он же лучше нашего HMAC из строки
 * запроса: подписано не только «кто», но и тело — сменить статус выгрузки
 * задним числом, перехватив адрес из логов прокси, больше нельзя.
 *
 * <p>Токен — обычный JWS HS256 на нашем {@code livekit.api-secret}: заголовок,
 * полезная нагрузка и подпись через точку. В нагрузке {@code iss} — ключ API,
 * {@code exp} — срок, {@code sha256} — SHA-256 тела в обычном base64.
 * Библиотеку JWT здесь не берём намеренно: она проверяет длину ключа и
 * отвергла бы короткий секрет LiveKit исключением на этапе разбора, то есть
 * пятисоткой вместо честного «не удостоверен».
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveKitWebhookVerifier {

    private static final String HMAC = "HmacSHA256";
    /** Мы принимаем только HS256: без этой проверки подошёл бы токен с {@code alg:none}. */
    private static final String EXPECTED_ALGORITHM = "HS256";

    private final ObjectMapper objectMapper;

    @Value("${livekit.api-key:}")
    private String apiKey;

    @Value("${livekit.api-secret:}")
    private String apiSecret;

    /**
     * @param authorization значение заголовка {@code Authorization} как его прислал LiveKit
     * @param body сырое тело запроса: по нему считается claim {@code sha256}
     */
    public boolean verify(String authorization, byte[] body, Instant now) {
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            log.warn("Вебхук Egress отклонён: livekit.api-key/api-secret не заданы");
            return false;
        }
        String token = strip(authorization);
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;
        }
        byte[] expected = hmac(parts[0] + "." + parts[1]);
        byte[] presented = decode(parts[2]);
        if (expected == null || presented == null || !MessageDigest.isEqual(expected, presented)) {
            return false;
        }
        JsonNode header = json(parts[0]);
        JsonNode claims = json(parts[1]);
        if (header == null || claims == null) {
            return false;
        }
        if (!EXPECTED_ALGORITHM.equals(header.path("alg").asText())) {
            return false;
        }
        if (!apiKey.equals(claims.path("iss").asText())) {
            return false;
        }
        long exp = claims.path("exp").asLong(0);
        if (exp > 0 && exp < now.getEpochSecond()) {
            return false;
        }
        return bodyMatches(claims.path("sha256").asText(""), body);
    }

    /**
     * Тело обязано быть подписано. Пустой claim принимался бы за «подписи тела
     * нет, и ладно» — тогда любой перехваченный токен переиграл бы уведомление
     * с другим содержимым.
     */
    private boolean bodyMatches(String claim, byte[] body) {
        if (claim.isBlank()) {
            return false;
        }
        try {
            byte[] declared = Base64.getDecoder().decode(claim);
            byte[] actual = MessageDigest.getInstance("SHA-256").digest(body == null ? new byte[0] : body);
            return MessageDigest.isEqual(declared, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] hmac(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), HMAC));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Не удалось посчитать подпись вебхука Egress: {}", e.getMessage());
            return null;
        }
    }

    private JsonNode json(String segment) {
        byte[] raw = decode(segment);
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] decode(String segment) {
        try {
            return Base64.getUrlDecoder().decode(segment);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** LiveKit шлёт голый токен, но префикс {@code Bearer} у прокси встречается. */
    private static String strip(String authorization) {
        String value = authorization == null ? "" : authorization.trim();
        return value.regionMatches(true, 0, "Bearer ", 0, 7) ? value.substring(7).trim() : value;
    }
}
