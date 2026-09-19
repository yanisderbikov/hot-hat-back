package ru.hothat.rating.store;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Дверь в {@code v2.season_player_standing}; наружу области не выходит. */
@Repository
interface SeasonPlayerStandings extends JpaRepository<SeasonPlayerStanding, SeasonPlayerStandingId> {

    List<SeasonPlayerStanding> findByBoardIdOrderByPointsDescPlayerIdAsc(Long boardId, Limit limit);

    /** Своя строка игрока: её показывает карточка профиля. */
    Optional<SeasonPlayerStanding> findByBoardIdAndPlayerId(Long boardId, UUID playerId);

    /**
     * Подтянуть личные очки до командных.
     *
     * <p>Личные очки растут на разницу командных с прошлой синхронизации, и
     * состояние этого правила — {@code synced_team_points}. Без него зачёт
     * второй партии начислил бы игроку все командные очки заново.
     *
     * <p>Разница считается в базе тем же выражением, что и вставка: у новой
     * строки {@code synced_team_points} равен нулю, поэтому первая партия
     * кладёт игроку ровно командные очки.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "insert into v2.season_player_standing "
            + "(board_id, player_id, team_id, points, synced_team_points, games, wins, updated_at) "
            + "values (:boardId, :playerId, :teamId, :teamPoints, :teamPoints, 1, :wins, now()) "
            + "on conflict (board_id, player_id) do update set "
            + "team_id = excluded.team_id, "
            + "points = v2.season_player_standing.points "
            + "       + (excluded.synced_team_points - v2.season_player_standing.synced_team_points), "
            + "synced_team_points = excluded.synced_team_points, "
            + "games = v2.season_player_standing.games + 1, "
            + "wins = v2.season_player_standing.wins + excluded.wins, "
            + "updated_at = now()", nativeQuery = true)
    void syncWithTeam(@Param("boardId") Long boardId, @Param("playerId") UUID playerId,
                      @Param("teamId") UUID teamId, @Param("teamPoints") int teamPoints,
                      @Param("wins") int wins);
}
