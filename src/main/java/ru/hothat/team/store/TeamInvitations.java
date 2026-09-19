package ru.hothat.team.store;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Дверь в {@code v2.team_invite}; за пределы области команды не выходит.
 *
 * <p>Список спрашивается ровно так, как под него сделан частичный индекс
 * {@code ix_team_invite_inbox}: только ждущие ответа, новые сверху.
 */
@Repository
interface TeamInvitations extends JpaRepository<TeamInvitation, UUID> {

    List<TeamInvitation> findByInviteePlayerIdAndStatusOrderByCreatedAtDesc(
            UUID inviteePlayerId, String status, Limit limit);

    /**
     * Приглашения распускаемой команды.
     *
     * <p>Внешний ключ и так унёс бы их каскадом, но строка, уже поднятая в
     * память, осталась бы в контексте живой: удаляем явно, чтобы «удалено в
     * базе» и «удалено у Hibernate» не разошлись.
     */
    List<TeamInvitation> findByTeamId(UUID teamId);
}
