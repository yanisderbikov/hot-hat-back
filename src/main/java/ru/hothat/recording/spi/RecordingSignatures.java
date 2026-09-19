package ru.hothat.recording.spi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import ru.hothat.config.ApiException;
import ru.hothat.recording.domain.RecordingKey;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Подписи области записей.
 *
 * <p>Считаются от секрета LiveKit и от пары «комната + партия», поэтому
 * подпись подходит только к своему адресу и только к своей партии.
 *
 * <p>Сравнение постоянного времени — не украшение: подпись проверяется на
 * каждом обращении рекордера, а обычный {@code equals} останавливается на
 * первом несовпавшем байте и тем самым рассказывает, сколько байтов угадано.
 */
@Service
@Primary
public class RecordingSignatures implements RecordingSignaturePort {

    private static final String VIEW = "hot-hat-recording-view";
    private static final String WEBHOOK = "hot-hat-egress-webhook";

    @Value("${livekit.api-secret:}")
    private String livekitSecret;

    @Override
    public String viewSignature(String roomId, int gameNumber) {
        return hmac(VIEW, roomId, gameNumber);
    }

    @Override
    public boolean verifyViewSignature(String roomId, int gameNumber, String signature) {
        return constantTimeEquals(viewSignature(roomId, gameNumber), signature);
    }

    @Override
    public String egressWebhookSignature(String roomId, int gameNumber) {
        return hmac(WEBHOOK, roomId, gameNumber);
    }

    @Override
    public boolean verifyEgressWebhookSignature(String roomId, int gameNumber, String signature) {
        return constantTimeEquals(egressWebhookSignature(roomId, gameNumber), signature);
    }

    private String hmac(String purpose, String roomId, int gameNumber) {
        if (livekitSecret == null || livekitSecret.isBlank()) {
            throw ApiException.of("LIVEKIT_NOT_CONFIGURED", 503);
        }
        String payload = purpose + "|" + new RecordingKey(roomId, gameNumber).roomId()
                + "|" + Math.max(0, gameNumber);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(livekitSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось подписать ссылку записи", e);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = (actual == null ? "" : actual).getBytes(StandardCharsets.UTF_8);
        return a.length == b.length && MessageDigest.isEqual(a, b);
    }
}
