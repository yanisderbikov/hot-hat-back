package ru.hothat.recording.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Дверь в {@code v2.recording_share} — кому запись открыта, кроме участников.
 *
 * <p>Право смотреть складывается из трёх источников: участник партии,
 * сохранивший её у себя и тот, кому её отправили в личной переписке. Третий —
 * это строка здесь.
 */
@Repository
interface RecordingShares extends JpaRepository<RecordingShare, RecordingShareId> {

    boolean existsByRecordingIdAndGrantee(UUID recordingId, UUID grantee);
}
