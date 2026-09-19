package ru.hothat.recording.store;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Дверь в {@code v2.recording_save} — кто положил запись к себе.
 *
 * <p>Заменяет jsonb-массив {@code saved_by}. Массив приходилось искать
 * оператором {@code @>} по всей таблице нативным запросом; здесь это индекс
 * {@code ix_recording_save_player}.
 */
@Repository
interface RecordingSaves extends JpaRepository<RecordingSave, RecordingSaveId> {

    /** Личная библиотека: свежие сохранения сверху, предел уезжает в базу. */
    List<RecordingSave> findByPlayerIdOrderBySavedAtDesc(UUID playerId, Limit limit);

    List<RecordingSave> findByRecordingIdInAndPlayerId(Collection<UUID> recordingIds, UUID playerId);

    long deleteByRecordingIdAndPlayerId(UUID recordingId, UUID playerId);
}
