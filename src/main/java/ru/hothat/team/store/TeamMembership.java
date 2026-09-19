package ru.hothat.team.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Участник команды: строка вместо элемента списка в jsonb.
 *
 * <p>Уникальный индекс по {@link #playerId} — без оговорки про состояние:
 * так ведёт себя код. Карточка игрока хранит одну команду, и «уже в команде»
 * срабатывает у основателя ещё до ответа напарника. Строка напарника при этом
 * появляется только при принятии приглашения, поэтому несколько ждущих
 * приглашений индекс не запрещает — их запрещает уникальность живого
 * приглашения со стороны команды.
 *
 * <p>Роль капитана заменила {@code owner_uid}: владелец команды перестал быть
 * отдельной колонкой рядом со списком участников, где он же лежал вторым
 * упоминанием.
 */
@Entity
@Table(name = "ranked_team_member", schema = "v2")
@IdClass(TeamMembershipId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class TeamMembership {

    @Id
    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    /** captain | partner — набор закрыт ограничением базы. */
    @Column(nullable = false, length = 16)
    private String role;

    /** pending | active — набор закрыт ограничением базы. */
    @Builder.Default
    @Column(nullable = false, length = 16)
    private String status = "pending";

    @Builder.Default
    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();
}
