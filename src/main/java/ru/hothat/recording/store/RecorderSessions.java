package ru.hothat.recording.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Дверь в {@code v2.recorder_session} — отметки страницы-рекордера. */
@Repository
interface RecorderSessions extends JpaRepository<RecorderSession, UUID> {

    List<RecorderSession> findByRecordingIdIn(Collection<UUID> recordingIds);
}
