package ru.hothat.machine.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.machine.api.dto.RecordingSweepResponseDTO;
import ru.hothat.machine.store.MaintenanceJournal;
import ru.hothat.recording.spi.RecordingLifecyclePort;
import ru.hothat.room.spi.RoomJanitorPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Уборка записей, которым вышел срок.
 *
 * <p>Что считать просроченным, решает область записей: у неё срок хранения и
 * счётчик сохранений лежат в своей строке, и сохранённые кем-то записи в
 * прогон не попадают вовсе. Машина держит аренду и ведёт журнал.
 *
 * <p>Порт {@link RoomJanitorPort} здесь ни при чём — упомянут только затем,
 * чтобы разница была видна: там убираются комнаты, тут файлы.
 */
@Service
@RequiredArgsConstructor
public class SweepExpiredRecordingsUseCase {

    /** Сколько записей просматривать за прогон; предел движка — двести. */
    private static final int BATCH_LIMIT = 100;

    private final RecordingLifecyclePort recordings;
    private final MaintenanceJournal journal;

    @PreAuthorize("hasRole('CRON')")
    public RecordingSweepResponseDTO run() {
        Optional<Long> run = journal.begin(MaintenanceJournal.RECORDING_SWEEP, "cron");
        if (run.isEmpty()) {
            return new RecordingSweepResponseDTO(0, 0, BATCH_LIMIT);
        }
        long runId = run.get();
        RecordingLifecyclePort.SweepResult result;
        try {
            result = recordings.sweepExpired(BATCH_LIMIT);
        } catch (RuntimeException e) {
            journal.failed(runId, e.getMessage());
            throw e;
        }
        List<MaintenanceJournal.Target> touched = new ArrayList<>(result.retired().size());
        for (String recordingId : result.retired()) {
            touched.add(new MaintenanceJournal.Target(MaintenanceJournal.TARGET_RECORDING,
                    recordingId, MaintenanceJournal.OUTCOME_DELETED, "expired"));
        }
        journal.succeeded(runId, result.checked(), result.affected(), touched);
        return new RecordingSweepResponseDTO(result.checked(), result.affected(), BATCH_LIMIT);
    }
}
