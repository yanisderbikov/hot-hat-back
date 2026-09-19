package ru.hothat.recording.store;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Дверь в {@code v2.recording}; за пределы области записей не выходит.
 *
 * <p>Поиск по паре «комната + партия» закрыт уникальным ключом
 * {@code ux_recording_game} — он же ключ идемпотентности старта.
 */
@Repository
interface Recordings extends JpaRepository<Recording, UUID> {

    Optional<Recording> findByRoomIdAndGameNumber(String roomId, Integer gameNumber);

    List<Recording> findByIdIn(Collection<UUID> ids);

    /** Админский каталог, свежие сверху: индекс {@code ix_recording_recent}. */
    List<Recording> findAllByOrderByCreatedAtDesc(Limit limit);

    /** Тот же каталог с отбором по дивизиону: {@code ix_recording_division}. */
    List<Recording> findByDivisionLanguageOrderByCreatedAtDesc(String divisionLanguage, Limit limit);
}
