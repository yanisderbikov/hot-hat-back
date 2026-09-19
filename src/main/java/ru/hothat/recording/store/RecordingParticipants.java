package ru.hothat.recording.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Дверь в {@code v2.recording_participant}.
 *
 * <p>Чтения только пачкой: список библиотеки на полсотни записей обязан стоить
 * один запрос, а не полсотни.
 */
@Repository
interface RecordingParticipants extends JpaRepository<RecordingParticipant, RecordingParticipantId> {

    List<RecordingParticipant> findByRecordingIdIn(Collection<UUID> recordingIds);

    /** «Записи, в которых я играл»: индекс {@code ix_recording_participant_player}. */
    boolean existsByRecordingIdAndPlayerId(UUID recordingId, UUID playerId);

    /**
     * Стереть состав перед тем, как записать заново.
     *
     * <p>{@code flushAutomatically} — чтобы правки паспорта, сделанные до
     * этого в той же транзакции, дошли до базы ДО очистки контекста;
     * {@code clearAutomatically} — чтобы следующие строки состава легли
     * чистыми вставками, а не поиском несуществующих строк по одной.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RecordingParticipant p where p.recordingId = :recordingId")
    void deleteByRecordingId(@Param("recordingId") UUID recordingId);
}
