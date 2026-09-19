package ru.hothat.recording.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Дверь в {@code v2.recording_team}. Чтения только пачкой. */
@Repository
interface RecordingTeams extends JpaRepository<RecordingTeam, RecordingTeamId> {

    List<RecordingTeam> findByRecordingIdIn(Collection<UUID> recordingIds);

    /** Стереть команды перед тем, как записать заново; оговорки — как у состава. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RecordingTeam t where t.recordingId = :recordingId")
    void deleteByRecordingId(@Param("recordingId") UUID recordingId);
}
