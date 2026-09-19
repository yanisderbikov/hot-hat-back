package ru.hothat.recording.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Дверь в {@code v2.recording_egress_job}.
 *
 * <p>Поиск по {@code egressId} закрыт уникальным ключом: вебхук приходит с
 * идентификатором задания и находит свою запись по нему, а не по разобранной
 * строке запроса.
 */
@Repository
interface EgressJobs extends JpaRepository<EgressJob, UUID> {

    List<EgressJob> findByRecordingIdIn(Collection<UUID> recordingIds);

    Optional<EgressJob> findByEgressId(String egressId);
}
