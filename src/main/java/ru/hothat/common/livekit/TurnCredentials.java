package ru.hothat.common.livekit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Короткоживущие учётки ретранслятора по схеме coturn.
 *
 * <p>Имя — «срок:идентификатор», пароль — его HMAC-SHA1 на общем секрете.
 * Лежит рядом с {@link LiveKitClient} по той же причине: это подпись
 * протокола, а не решение о допуске. Кому выдавать учётку, решают области —
 * комната для своих мест и видео-чат для своих участников, — и у каждой свой
 * переходник в {@code store}; общая здесь одна формула, чтобы две области не
 * разошлись в том, как считается пароль.
 *
 * <p>Секрета нет или адресов нет — учётки нет. Это не ошибка: без
 * ретранслятора связь держится напрямую, и отвечать отказом значило бы
 * выключить видео там, где оно и так работает.
 */
@Slf4j
@Component
public class TurnCredentials {

    private final List<String> urls;
    private final String secret;
    private final long ttlSeconds;

    public TurnCredentials(@Value("${turn.urls:}") String turnUrls,
                           @Value("${turn.secret:}") String turnSecret,
                           @Value("${turn.ttl-seconds:7200}") long turnTtlSeconds) {
        this.urls = Arrays.stream((turnUrls == null ? "" : turnUrls).split(","))
                .map(String::trim)
                .filter(value -> value.toLowerCase().startsWith("turn:")
                        || value.toLowerCase().startsWith("turns:"))
                .toList();
        this.secret = turnSecret == null ? "" : turnSecret.trim();
        this.ttlSeconds = Math.min(21600, Math.max(600, turnTtlSeconds));
    }

    /** Учётка для участника; пусто — ретранслятор не настроен. */
    public Optional<Grant> issue(String identity) {
        if (secret.isBlank() || urls.isEmpty()) {
            return Optional.empty();
        }
        long expiresAt = System.currentTimeMillis() / 1000 + ttlSeconds;
        String username = expiresAt + ":" + safeIdentity(identity);
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            String credential = Base64.getEncoder()
                    .encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
            return Optional.of(new Grant(urls, username, credential, ttlSeconds, expiresAt));
        } catch (Exception e) {
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

    /**
     * Учётка: адреса, имя, пароль и срок.
     *
     * @param expiresAtSeconds момент, до которого учётка жива, — тот самый,
     *                         что стоит началом {@code username}
     */
    public record Grant(List<String> urls, String username, String credential,
                        long ttlSeconds, long expiresAtSeconds) {
    }
}
