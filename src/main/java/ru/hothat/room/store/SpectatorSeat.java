package ru.hothat.room.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Место зрителя.
 *
 * <p>Как и у места игрока, здесь нет ни имени, ни аватара: то и другое —
 * копии чужих данных (§6.3).
 *
 * <p>Внешний ключ на учётку в базе ЕСТЬ, в отличие от места игрока: зрителем
 * бывает только человек, ботов в зал не сажают.
 */
@Entity
@Table(name = "room_spectator", schema = "v2")
@IdClass(SpectatorSeatId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class SpectatorSeat {

    @Id
    @Column(name = "room_id", nullable = false, updatable = false, length = 24)
    private String roomId;

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    @Builder.Default
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
