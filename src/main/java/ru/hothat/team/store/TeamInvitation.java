package ru.hothat.team.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Приглашение напарника в команду.
 *
 * <p>Копии чужих данных сюда не переехали: сегодня приглашение носит с собой
 * {@code team_name}, {@code owner_nickname} и {@code division_language} —
 * всё это отдают команда и карточка игрока, и всё это устаревает.
 *
 * <p>{@link #expiresAt} допускает пустоту: срок жизни приглашения в команду
 * заложен схемой по плану, но сегодня такого правила нет вовсе — приглашение
 * висит, пока на него не ответят. Писать в колонку станет тот, кто заведёт
 * правило.
 */
@Entity
@Table(name = "team_invite", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class TeamInvitation {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Column(name = "invitee_player_id", nullable = false, updatable = false)
    private UUID inviteePlayerId;

    /** pending | accepted | declined — набор закрыт ограничением базы. */
    @Builder.Default
    @Column(nullable = false, length = 16)
    private String status = "pending";

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
