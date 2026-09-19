package ru.hothat.recording.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Дверь в {@code v2.recording_artifact} — файл записи в хранилище. */
@Repository
interface RecordingArtifacts extends JpaRepository<RecordingArtifact, UUID> {

    List<RecordingArtifact> findByRecordingIdIn(Collection<UUID> recordingIds);
}
