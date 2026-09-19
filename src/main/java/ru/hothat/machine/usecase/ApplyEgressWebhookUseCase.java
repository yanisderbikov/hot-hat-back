package ru.hothat.machine.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.machine.api.dto.EgressWebhookAckResponseDTO;
import ru.hothat.machine.api.dto.EgressWebhookOutcome;
import ru.hothat.machine.api.dto.LiveKitEgressFileView;
import ru.hothat.machine.api.dto.LiveKitEgressInfoView;
import ru.hothat.machine.api.dto.LiveKitEgressWebhookRequestDTO;
import ru.hothat.recording.spi.RecordingLifecyclePort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Уведомление LiveKit о выгрузке записи.
 *
 * <p>Машинная половина делает ровно две вещи: отсеивает чужие события и даёт
 * событию имя доставки. Что записать в таблицы записи — решает область
 * записей: здесь про её девять таблиц не известно ничего.
 *
 * <p>Имя доставки LiveKit не присылает, поэтому его место занимает хеш тела —
 * так и предписывает схема журнала. Без него повтор доставки неотличим от
 * нового события, и завершение записи, пришедшее дважды, применялось дважды.
 */
@Service
@RequiredArgsConstructor
public class ApplyEgressWebhookUseCase {

    private static final String EGRESS_EVENT_PREFIX = "egress_";

    private final RecordingLifecyclePort recordings;

    @PreAuthorize("hasRole('EGRESS')")
    public EgressWebhookAckResponseDTO run(String roomId, int gameNumber,
                                           LiveKitEgressWebhookRequestDTO request) {
        String event = request.event();
        if (!event.startsWith(EGRESS_EVENT_PREFIX)) {
            return new EgressWebhookAckResponseDTO(
                    EgressWebhookOutcome.IGNORED_NOT_EGRESS_EVENT, event, null, null, null);
        }
        if (request.egressInfo() == null) {
            return new EgressWebhookAckResponseDTO(
                    EgressWebhookOutcome.IGNORED_NO_EGRESS_INFO, event, null, null, null);
        }
        Map<String, Object> info = info(request.egressInfo());
        RecordingLifecyclePort.WebhookResult result = recordings.applyEgressWebhook(
                roomId, gameNumber, deliveryId(roomId, gameNumber, event, info), event, info);
        return new EgressWebhookAckResponseDTO(outcome(result.outcome()), event,
                result.recordingId(), result.stage(),
                result.outcome() == RecordingLifecyclePort.Webhook.APPLIED ? result.liveKitCode() : null);
    }

    private static EgressWebhookOutcome outcome(RecordingLifecyclePort.Webhook webhook) {
        return switch (webhook) {
            case APPLIED -> EgressWebhookOutcome.APPLIED;
            case IGNORED_DUPLICATE -> EgressWebhookOutcome.IGNORED_DUPLICATE_DELIVERY;
            case IGNORED_RECORDING_NOT_FOUND -> EgressWebhookOutcome.IGNORED_RECORDING_NOT_FOUND;
            case IGNORED_EGRESS_ID_MISMATCH -> EgressWebhookOutcome.IGNORED_EGRESS_ID_MISMATCH;
        };
    }

    /**
     * Имя доставки — отпечаток самого уведомления вместе с адресом, на который
     * оно пришло. Повтор той же доставки даёт тот же отпечаток, а следующее
     * состояние того же задания — уже другой.
     */
    private static String deliveryId(String roomId, int gameNumber, String event, Map<String, Object> info) {
        String payload = roomId + "|" + gameNumber + "|" + event + "|" + info;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Не удалось назвать доставку вебхука", e);
        }
    }

    /**
     * Тело уведомления как есть — оно уезжает в журнал целиком.
     *
     * <p>Ключи те же, что у LiveKit: журнал ведётся ради разбора инцидентов, и
     * переименовывать в нём чужие поля значило бы разбирать не то, что пришло.
     */
    private static Map<String, Object> info(LiveKitEgressInfoView view) {
        Map<String, Object> info = new LinkedHashMap<>();
        if (view.egressId() != null) {
            info.put("egress_id", view.egressId());
        }
        if (view.status() != null) {
            info.put("status", view.status());
        }
        if (view.error() != null) {
            info.put("error", view.error());
        }
        if (view.details() != null) {
            info.put("details", view.details());
        }
        if (view.endedAt() != null) {
            info.put("ended_at", view.endedAt());
        }
        List<Map<String, Object>> files = new ArrayList<>();
        for (LiveKitEgressFileView file : view.fileResults() == null
                ? List.<LiveKitEgressFileView>of() : view.fileResults()) {
            Map<String, Object> row = new LinkedHashMap<>();
            if (file.duration() != null) {
                row.put("duration", file.duration());
            }
            if (file.size() != null) {
                row.put("size", file.size());
            }
            if (file.filename() != null) {
                row.put("filename", file.filename());
            }
            files.add(row);
        }
        info.put("file_results", files);
        return info;
    }
}
