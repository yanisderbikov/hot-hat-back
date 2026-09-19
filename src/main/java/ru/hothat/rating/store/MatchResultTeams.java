package ru.hothat.rating.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Дверь в {@code v2.match_result_team}; наружу области не выходит.
 *
 * <p>Заменяет jsonb {@code culprit_team_ids} и восстанавливает то, чего в
 * старой схеме нет вовсе: сколько именно очков команда получила за эту партию.
 */
@Repository
interface MatchResultTeams extends JpaRepository<MatchResultTeam, MatchResultTeamId> {
}
