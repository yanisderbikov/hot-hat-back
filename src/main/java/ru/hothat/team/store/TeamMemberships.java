package ru.hothat.team.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Дверь в {@code v2.ranked_team_member} — таблицу, ради которой затевалась
 * развязка команды: состав перестал быть двумя jsonb внутри команды и третьей
 * копией в карточке игрока.
 *
 * <p>«В какой команде игрок» — это один запрос по уникальному индексу
 * {@code ux_ranked_team_member_player}, а не чтение колонки
 * {@code app_user.ranked_team_id}, которую писало второе место.
 */
@Repository
interface TeamMemberships extends JpaRepository<TeamMembership, TeamMembershipId> {

    Optional<TeamMembership> findByPlayerId(UUID playerId);

    /**
     * Состав команды. Порядок по роли, а не по времени вступления: капитан
     * обязан стоять первым — экраны показывают пару в этом порядке, и
     * {@code captain} лексикографически меньше {@code partner}.
     */
    List<TeamMembership> findByTeamIdOrderByRoleAsc(UUID teamId);

    /** Составы сразу многих команд — один запрос на всю таблицу рейтинга. */
    List<TeamMembership> findByTeamIdInOrderByRoleAsc(Collection<UUID> teamIds);
}
