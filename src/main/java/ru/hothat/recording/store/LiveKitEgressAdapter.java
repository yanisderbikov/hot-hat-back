package ru.hothat.recording.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.hothat.common.livekit.LiveKitClient;
import ru.hothat.config.HotHatProperties;
import ru.hothat.recording.domain.EgressStage;
import ru.hothat.recording.port.EgressControlPort;
import ru.hothat.recording.spi.RecordingSignaturePort;
import ru.hothat.util.Ids;
import ru.hothat.util.Json;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Задание Egress через сегодняшний клиент LiveKit.
 *
 * <p>Адаптер и есть та черта, за которой кончается область записей: сценарии
 * знают {@link EgressControlPort}, а не форму запросов LiveKit. Разбор ответа —
 * тоже здесь: наружу уезжает {@link Snapshot}, в котором сырой код состояния
 * лежит одним полем и дальше журнала вебхуков не идёт.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveKitEgressAdapter implements EgressControlPort {

    /** Скрытый участник комнаты: рекордер снимает, но в сетке не показывается. */
    private static final long RECORDER_TOKEN_TTL_SECONDS = 7200;
    private static final String LAYOUT = "hot-hat";

    private final LiveKitClient liveKit;
    // Адаптер бакета, а не порт: сюда нужны настройки выгрузки (endpoint,
    // bucket), которые сценариям не показываются и в порту им не место.
    private final RecordingBucketAdapter storage;
    private final RecordingSignaturePort signatures;
    private final HotHatProperties properties;

    @Value("${livekit.api-key:}")
    private String livekitKey;

    @Value("${livekit.api-secret:}")
    private String livekitSecret;

    @Value("${livekit.url:}")
    private String livekitUrl;

    @Value("${s3.access-key:}")
    private String s3AccessKey;

    @Value("${s3.secret-key:}")
    private String s3SecretKey;

    @Value("${s3.region:us-east-1}")
    private String s3Region;

    @Value("${s3.force-path-style:true}")
    private boolean s3ForcePathStyle;

    @Override
    public boolean available() {
        return livekitSecret != null && !livekitSecret.isBlank();
    }

    @Override
    public Snapshot start(String roomId, int gameNumber, String objectPath) {
        Map<String, Object> fileOutput = new LinkedHashMap<>();
        fileOutput.put("filepath", objectPath);
        fileOutput.put("disable_manifest", true);
        fileOutput.put("s3", s3UploadConfig());

        Map<String, Object> webhook = new LinkedHashMap<>();
        webhook.put("url", egressWebhookUrl(roomId, gameNumber));
        webhook.put("signing_key", livekitKey);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("url", webEgressUrl(roomId, gameNumber));
        request.put("audio_only", false);
        request.put("video_only", false);
        request.put("file_outputs", List.of(fileOutput));
        request.put("webhooks", List.of(webhook));
        return snapshot(liveKit.egress("StartWebEgress", request));
    }

    /**
     * Повторный {@code StopEgress} безопасен: уже завершённое задание LiveKit
     * просто подтверждает, а зависшее ACTIVE наконец закрывает и выгружает файл.
     * Поэтому «мягкие» отказы гасятся, а не поднимаются наверх — остановка
     * обязана быть идемпотентной, её зовут из трёх мест сразу.
     */
    @Override
    public Optional<Snapshot> stop(String egressId) {
        try {
            return Optional.of(snapshot(liveKit.egress("StopEgress", Map.of("egress_id", egressId))));
        } catch (RuntimeException e) {
            String message = Json.str(e.getMessage()).toLowerCase();
            boolean benign = message.contains("not found") || message.contains("not active")
                    || message.contains("already") || message.contains("complete");
            if (!benign) {
                throw e;
            }
            log.debug("Остановка Egress {} уже не нужна: {}", egressId, e.getMessage());
            return describe(egressId);
        }
    }

    @Override
    public Optional<Snapshot> describe(String egressId) {
        try {
            Map<String, Object> listed = liveKit.egress("ListEgress", Map.of("egress_id", egressId));
            List<Object> items = Json.list(listed.get("items"));
            for (Object item : items) {
                Map<String, Object> info = Json.map(item);
                if (egressId.equals(egressId(info))) {
                    return Optional.of(snapshot(info));
                }
            }
            return items.isEmpty() ? Optional.empty() : Optional.of(snapshot(Json.map(items.get(0))));
        } catch (RuntimeException e) {
            // Недоступный LiveKit — не повод ронять сценарий: карточка покажет
            // последнее известное состояние, а опрос повторится.
            log.warn("Статус Egress {} пока недоступен: {}", egressId, e.getMessage());
            return Optional.empty();
        }
    }

    /** Разбор ответа LiveKit в наши понятия — единственное место, где он есть. */
    public static Snapshot snapshot(Map<String, Object> info) {
        List<Object> results = Json.list(info.getOrDefault("file_results", info.get("fileResults")));
        Map<String, Object> file = results.isEmpty() ? Map.of() : Json.map(results.get(0));
        long endedAtNs = Json.num(info.getOrDefault("ended_at", info.get("endedAt")));
        return new Snapshot(
                egressId(info),
                EgressStage.liveKitCode(info.get("status")),
                Json.num(file.get("duration")),
                Json.num(file.get("size")),
                Json.str(file.get("filename")),
                Json.str(info.getOrDefault("error", info.getOrDefault("details", ""))),
                // LiveKit отдаёт время в наносекундах; карточка ждёт миллисекунды.
                endedAtNs > 0 ? Math.round(endedAtNs / 1_000_000.0) : null);
    }

    private static String egressId(Map<String, Object> info) {
        return Json.str(info.getOrDefault("egress_id", info.getOrDefault("egressId", "")));
    }

    private Map<String, Object> s3UploadConfig() {
        Map<String, Object> s3 = new LinkedHashMap<>();
        s3.put("access_key", s3AccessKey);
        s3.put("secret", s3SecretKey);
        s3.put("region", s3Region);
        s3.put("endpoint", storage.endpoint());
        s3.put("bucket", storage.bucket());
        s3.put("force_path_style", s3ForcePathStyle);
        return s3;
    }

    /**
     * Egress открывает обычный клиент HOT-HAT в режиме рекордера: он входит по
     * подписи и показывает ту же игру, что видят участники.
     */
    public String recorderPageUrl(String roomId, int gameNumber) {
        return properties.publicSiteUrl() + "/?recorder=1"
                + "&roomId=" + encode(roomId)
                + "&gameNumber=" + Math.max(0, gameNumber)
                + "&sig=" + encode(signatures.viewSignature(roomId, gameNumber));
    }

    private String webEgressUrl(String roomId, int gameNumber) {
        String recorderToken = liveKit.accessToken(new LiveKitClient.AccessTokenRequest(
                "hot-hat-recorder-" + Ids.hex(8), "HOT-HAT Recorder", roomId, null,
                false, true, true, true, true, false, false, true, RECORDER_TOKEN_TTL_SECONDS));
        return recorderPageUrl(roomId, gameNumber)
                + "&url=" + encode(livekitUrl)
                + "&token=" + encode(recorderToken)
                + "&layout=" + LAYOUT;
    }

    private String egressWebhookUrl(String roomId, int gameNumber) {
        return properties.apiOrigin() + "/api/recording-egress"
                + "?roomId=" + encode(roomId)
                + "&gameNumber=" + Math.max(0, gameNumber)
                + "&sig=" + encode(signatures.egressWebhookSignature(roomId, gameNumber));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
