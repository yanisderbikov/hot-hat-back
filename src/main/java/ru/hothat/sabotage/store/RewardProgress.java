package ru.hothat.sabotage.store;

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

import java.util.UUID;

/**
 * Прогресс команды к редким диверсиям.
 *
 * <p>Заменяет пару jsonb {@code special_reward_progress_by_team} и
 * {@code special_reward_cursor_by_team} — две карты «команда → число» в строке
 * комнаты, которые обязаны были меняться вместе и потому расходились.
 *
 * <p>{@link #guessedTotal} растёт атомарным {@code UPDATE} (§6.4).
 * {@link #recipientCursor} нужен, чтобы редкие награды не копились у одного
 * игрока команды.
 */
@Entity
@Table(name = "match_reward_progress", schema = "v2")
@IdClass(RewardProgressId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RewardProgress {

    @Id
    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Id
    @Column(name = "room_team_id", nullable = false, updatable = false)
    private UUID roomTeamId;

    /** Сколько слов команда угадала за партию: по этому счёту идут награды. */
    @Builder.Default
    @Column(name = "guessed_total", nullable = false)
    private Integer guessedTotal = 0;

    @Builder.Default
    @Column(name = "recipient_cursor", nullable = false)
    private Integer recipientCursor = 0;
}
