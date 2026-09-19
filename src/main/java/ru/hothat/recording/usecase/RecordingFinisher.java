package ru.hothat.recording.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.hothat.config.ApiException;
import ru.hothat.recording.domain.EgressStage;
import ru.hothat.recording.domain.RecordingKey;
import ru.hothat.recording.port.EgressControlPort;
import ru.hothat.recording.port.RecordingStoragePort;
import ru.hothat.recording.port.RoomSnapshotPort;
import ru.hothat.recording.spi.RecordingLifecyclePort;
import ru.hothat.recording.store.RecordingStore;

import java.util.Optional;
import java.util.UUID;

/**
 * Остановка записи — одна на все три дороги.
 *
 * <p>Дорог действительно три, и они гоняются между собой: игрок нажал «хватит»,
 * рекордер досмотрел церемонию награждения, сервер сам убирает брошенную
 * комнату. Любая может прийти второй, поэтому идемпотентность здесь не
 * удобство, а требование: повторный {@code StopEgress} LiveKit переносит
 * спокойно, а зависшую активную запись наконец закрывает.
 *
 * <p>Разговор с LiveKit идёт СНАРУЖИ транзакции — по той же причине, что и на
 * старте. Транзакций две: «мы просим остановиться» и «вот что ответили».
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordingFinisher {

    private final RoomSnapshotPort rooms;
    private final EgressControlPort egress;
    private final RecordingStoragePort storage;
    private final RecordingWrites writes;
    private final RecordingStore store;

    /**
     * @param actorUid    кто останавливает; {@code null} — остановка без человека
     * @param strictRoom  бросать ли 404, если комнаты уже нет: у адреса игрока
     *                    это ошибка, у системной уборки — обычное дело
     */
    public RecordingLifecyclePort.FinishResult finish(RecordingKey key, String actorUid,
                                                      String reason, boolean strictRoom) {
        Optional<RoomSnapshotPort.RoomSnapshot> found = rooms.find(key.roomId());
        if (found.isEmpty()) {
            if (strictRoom) {
                throw ApiException.of("ROOM_NOT_FOUND", 404);
            }
            return outcome(RecordingLifecyclePort.Finish.NOT_STARTED, null, null);
        }
        RoomSnapshotPort.RoomSnapshot room = found.get();
        if (!room.recordingEnabled()) {
            return outcome(RecordingLifecyclePort.Finish.RECORDING_DISABLED, null, null);
        }
        Optional<RecordingStore.Card> existing = store.card(key);
        if (existing.isEmpty()) {
            return outcome(RecordingLifecyclePort.Finish.NOT_STARTED, null, null);
        }
        RecordingStore.Card card = existing.get();
        UUID id = card.passport().id();
        if (actorUid != null && !store.isParticipant(id, actorUid) && !rooms.isMember(key.roomId(), actorUid)) {
            throw ApiException.of("PLAYER_NOT_FOUND", 403);
        }
        if (alreadyFinished(card)) {
            return outcome(RecordingLifecyclePort.Finish.ALREADY_FINISHED, key.publicId(), card.stage());
        }

        writes.markStopRequested(id);
        String egressId = card.job() == null ? null : card.job().egressId();
        Optional<EgressControlPort.Snapshot> answer = Optional.empty();
        if (egressId != null && !egressId.isBlank()) {
            try {
                // Снаружи транзакции.
                answer = egress.stop(egressId);
            } catch (RuntimeException e) {
                // Партия уже кончилась, и невозможность закрыть файл — беда
                // записи, а не игроков: их сценарий обязан дойти до конца.
                log.warn("Остановка записи {} не удалась: {}", key.publicId(), e.getMessage());
            }
        }
        EgressStage stage = answer
                .map(snapshot -> writes.applySnapshot(id, key, snapshot, storage.bucket()))
                .orElse(EgressStage.ofWire(card.stage()));
        // Итог партии замораживается ровно здесь: на старте счёта ещё нет, а
        // прогретая запись заводится вообще до первого хода.
        writes.freezeResults(id, room);
        if (reason != null && !reason.isBlank()) {
            log.info("Запись {} остановлена: {}", key.publicId(), reason);
        }
        return outcome(RecordingLifecyclePort.Finish.FINISHED, key.publicId(), stage.wire());
    }

    /**
     * Останавливать нечего: задание кончилось и время конца записано.
     *
     * <p>Проверяется именно пара «стадия + время», а не одна стадия: задание,
     * которое мы только попросили остановить, ещё не завершено, и второй
     * вызов обязан довести остановку до конца.
     */
    private static boolean alreadyFinished(RecordingStore.Card card) {
        if (EgressStage.DELETED.equals(card.stage())) {
            return true;
        }
        RecordingStore.Job job = card.job();
        return job != null && job.endedAtMs() != null && EgressStage.ofWire(job.state()).finished();
    }

    private static RecordingLifecyclePort.FinishResult outcome(RecordingLifecyclePort.Finish outcome,
                                                               String recordingId, String stage) {
        return new RecordingLifecyclePort.FinishResult(outcome, recordingId, stage);
    }
}
