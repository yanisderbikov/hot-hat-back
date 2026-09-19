package ru.hothat.recording.spi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.hothat.game.port.RecordingCommandPort;
import ru.hothat.recording.domain.EgressStage;
import ru.hothat.recording.domain.RecordingKey;
import ru.hothat.recording.port.EgressControlPort;
import ru.hothat.recording.port.RecordingStoragePort;
import ru.hothat.recording.store.LiveKitEgressAdapter;
import ru.hothat.recording.store.RecordingStore;
import ru.hothat.recording.usecase.RecordingFinisher;
import ru.hothat.recording.usecase.RecordingSweeper;
import ru.hothat.recording.usecase.RecordingWrites;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Область записей глазами чужих областей.
 *
 * <p>Один класс на два порта, и это не совпадение: {@link RecordingCommandPort}
 * — то, что партия умеет сказать записи («снимать больше нечего»), а
 * {@link RecordingLifecyclePort} — то же самое плюс машинные события. Партия
 * не должна знать про вебхуки, а машина — про правила партии.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingLifecycle implements RecordingLifecyclePort, RecordingCommandPort {

    private final RecordingFinisher finisher;
    private final RecordingSweeper sweeper;
    private final RecordingWrites writes;
    private final RecordingStore store;
    private final RecordingStoragePort storage;

    // ─────────────────────── партия ───────────────────────

    /**
     * Остановка по воле партии.
     *
     * <p>Отказ не должен ронять сценарий: партия уже кончилась, и невозможность
     * закрыть файл — беда записи, а не игроков.
     */
    @Override
    public void finish(String roomId, int gameNumber, String reason) {
        try {
            finishBySystem(roomId, gameNumber, reason);
        } catch (RuntimeException e) {
            log.warn("Не удалось остановить запись комнаты {}: {}", roomId, e.getMessage());
        }
    }

    // ─────────────────────── машинная половина ───────────────────────

    @Override
    public FinishResult finishBySystem(String roomId, int gameNumber, String reason) {
        // strictRoom = false: комнаты может уже не быть — её и убирает та же
        // уборка, которая просит остановить запись.
        return finisher.finish(new RecordingKey(roomId, gameNumber), null, reason, false);
    }

    @Override
    public void markRecorderReady(String roomId, int gameNumber, String phase, String identity) {
        withRecording(roomId, gameNumber, id -> writes.markRecorderReady(id, phase, identity));
    }

    @Override
    public void markRecorderStarted(String roomId, int gameNumber, String phase, String identity) {
        withRecording(roomId, gameNumber, id -> writes.markRecorderStarted(id, phase, identity));
    }

    /** Церемония досмотрена: рекордер отмечает шаг и просит остановить съёмку. */
    public FinishResult completeCeremony(String roomId, int gameNumber) {
        withRecording(roomId, gameNumber, writes::markCeremonyCompleted);
        return finishBySystem(roomId, gameNumber, REASON_CEREMONY);
    }

    /**
     * Уведомления рекордера приходят и до старта записи, и после её конца.
     * Заводить запись под них нельзя: паспорт партии пишет тот, кто нажал
     * «снимать», и только он.
     */
    private void withRecording(String roomId, int gameNumber, java.util.function.Consumer<UUID> change) {
        RecordingKey key = new RecordingKey(roomId, gameNumber);
        if (!key.valid() || !store.exists(key)) {
            log.debug("Сигнал рекордера без записи: {}", key.publicId());
            return;
        }
        change.accept(store.idOf(key));
    }

    @Override
    public WebhookResult applyEgressWebhook(String roomId, int gameNumber, String deliveryId,
                                            String eventName, Map<String, Object> egressInfo) {
        EgressControlPort.Snapshot snapshot = LiveKitEgressAdapter.snapshot(egressInfo);
        RecordingKey key = new RecordingKey(roomId, gameNumber);
        // Идентификатор задания — ключ надёжнее строки запроса: её LiveKit
        // берёт из адреса, который мы сами дали ему на старте, а задание он
        // называет сам.
        Optional<UUID> byEgress = store.byEgressId(snapshot.egressId());
        Optional<RecordingStore.Card> found = byEgress.isPresent()
                ? store.card(byEgress.get()) : store.card(key);
        if (found.isEmpty()) {
            writes.journal(deliveryId, null, eventName, snapshot.egressId(),
                    rawState(egressInfo), false, egressInfo);
            return new WebhookResult(Webhook.IGNORED_RECORDING_NOT_FOUND, null, null, snapshot.liveKitCode());
        }
        RecordingStore.Card card = found.get();
        UUID id = card.passport().id();
        String known = card.job() == null ? null : card.job().egressId();
        if (known != null && !known.isBlank() && !snapshot.egressId().isEmpty() && !known.equals(snapshot.egressId())) {
            writes.journal(deliveryId, id, eventName, snapshot.egressId(),
                    rawState(egressInfo), false, egressInfo);
            return new WebhookResult(Webhook.IGNORED_EGRESS_ID_MISMATCH,
                    card.passport().recordingId(), null, snapshot.liveKitCode());
        }
        // Журнал ведётся ДО применения: уникальный ключ доставки — и есть
        // защита от повтора. Раньше повтор применялся второй раз, потому что
        // отличить его было не по чему.
        if (!writes.journal(deliveryId, id, eventName, snapshot.egressId(),
                rawState(egressInfo), true, egressInfo)) {
            return new WebhookResult(Webhook.IGNORED_DUPLICATE,
                    card.passport().recordingId(), card.stage(), snapshot.liveKitCode());
        }
        EgressStage stage = writes.applySnapshot(id, key, snapshot, storage.bucket());
        return new WebhookResult(Webhook.APPLIED, card.passport().recordingId(),
                stage.wire(), snapshot.liveKitCode());
    }

    @Override
    public SweepResult sweepExpired(int limit) {
        return sweeper.sweep(limit);
    }

    /** Сырой код LiveKit хранится только в журнале и дальше него не идёт. */
    private static String rawState(Map<String, Object> egressInfo) {
        Object status = egressInfo == null ? null : egressInfo.get("status");
        return status == null ? null : status.toString();
    }
}
