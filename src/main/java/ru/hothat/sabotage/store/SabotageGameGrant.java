package ru.hothat.sabotage.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
 * Журнал списаний бесплатных партий с диверсиями.
 *
 * <p>Заменяет {@code sabotage_game_use}, где ключом была склейка
 * «roomId-gameNumber-uid» в {@code VARCHAR(200)} (§6.3, «дубли ключа»). Здесь
 * та же тройка — уникальный индекс, и он же ключ идемпотентности: повторный
 * старт той же партии не спишет вторую бесплатную игру.
 *
 * <p>{@link #roomId} БЕЗ внешнего ключа намеренно, хотя таблица комнат рядом:
 * списание обязано пережить уборку комнаты. Бесплатная партия израсходована,
 * даже если комнаты больше нет, и каскад молча вернул бы её игроку.
 */
@Entity
@Table(name = "sabotage_game_grant", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class SabotageGameGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    @Column(name = "room_id", nullable = false, updatable = false, length = 24)
    private String roomId;

    @Column(name = "game_number", nullable = false, updatable = false)
    private Integer gameNumber;

    @Builder.Default
    @Column(name = "consumed_at", nullable = false, updatable = false)
    private Instant consumedAt = Instant.now();
}
