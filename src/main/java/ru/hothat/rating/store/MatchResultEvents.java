package ru.hothat.rating.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Дверь в {@code v2.match_result_event}; наружу области не выходит.
 *
 * <p>Это барьер идемпотентности. Раньше «уже засчитано?» было чтением перед
 * вставкой, и два клиента, дожавшие кнопку одновременно, начисляли очки
 * дважды. Здесь первым делом идёт вставка, и второй зачёт отвергает база.
 */
@Repository
interface MatchResultEvents extends JpaRepository<MatchResultEvent, MatchResultEventId> {

    /**
     * Занять партию.
     *
     * @return {@code 1} — зачёт наш и очки считать нам; {@code 0} — партию
     *         уже засчитал кто-то другой
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "insert into v2.match_result_event "
            + "(room_id, game_number, board_id, outcome, technical, reason, recorded_by) "
            + "values (:roomId, :gameNumber, :boardId, :outcome, :technical, :reason, :recordedBy) "
            + "on conflict do nothing", nativeQuery = true)
    int claim(@Param("roomId") String roomId, @Param("gameNumber") Integer gameNumber,
              @Param("boardId") Long boardId, @Param("outcome") String outcome,
              @Param("technical") boolean technical, @Param("reason") String reason,
              @Param("recordedBy") UUID recordedBy);
}
