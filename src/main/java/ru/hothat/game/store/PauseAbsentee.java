package ru.hothat.game.store;

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
 * Кого не хватает в LiveKit прямо сейчас.
 *
 * <p>Заменяет пару jsonb {@code pause_missing_uids} +
 * {@code pause_missing_names}, где имена были второй копией чужих данных, а
 * связь между двумя массивами держалась порядком элементов.
 *
 * <p>{@code @Version} нет намеренно (§6.4): строку заводит и убирает сверка
 * присутствия, и побеждает последняя.
 */
@Entity
@Table(name = "match_pause_absentee", schema = "v2")
@IdClass(PauseAbsenteeId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class PauseAbsentee {

    @Id
    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    @Builder.Default
    @Column(name = "missing_since", nullable = false)
    private Instant missingSince = Instant.now();
}
