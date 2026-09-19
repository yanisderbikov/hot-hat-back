package ru.hothat.rating.store;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Дверь в {@code v2.season_team_standing}; наружу области не выходит.
 *
 * <p>Порядок запрашивается ровно тот, под который сделан индекс
 * {@code ix_season_team_standing_board}: очки по убыванию, команда третьим
 * полем. Без неё две соседние страницы при равных очках показали бы одну
 * команду дважды.
 */
@Repository
interface SeasonTeamStandings extends JpaRepository<SeasonTeamStanding, SeasonTeamStandingId> {

    List<SeasonTeamStanding> findByBoardIdOrderByPointsDescTeamIdAsc(Long boardId, Limit limit);

    List<SeasonTeamStanding> findByBoardIdAndTeamIdIn(Long boardId, Collection<UUID> teamIds);

    Optional<SeasonTeamStanding> findByBoardIdAndTeamId(Long boardId, UUID teamId);

    /**
     * Начислить команде за партию — прибавлением в базе, а не чтением в память.
     *
     * <p>Это и есть развязка: раньше строку поднимали целиком, меняли поля и
     * писали обратно, и два зачёта, дошедшие одновременно, теряли очки друг
     * друга. Здесь значение не читается вовсе, поэтому и терять нечего.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "insert into v2.season_team_standing "
            + "(board_id, team_id, points, games, wins, technical_forfeits, updated_at) "
            + "values (:boardId, :teamId, :points, 1, :wins, :forfeits, now()) "
            + "on conflict (board_id, team_id) do update set "
            + "points = v2.season_team_standing.points + excluded.points, "
            + "games = v2.season_team_standing.games + 1, "
            + "wins = v2.season_team_standing.wins + excluded.wins, "
            + "technical_forfeits = v2.season_team_standing.technical_forfeits + excluded.technical_forfeits, "
            + "updated_at = now()", nativeQuery = true)
    void addTeamResult(@Param("boardId") Long boardId, @Param("teamId") UUID teamId,
                   @Param("points") int points, @Param("wins") int wins, @Param("forfeits") int forfeits);
}
