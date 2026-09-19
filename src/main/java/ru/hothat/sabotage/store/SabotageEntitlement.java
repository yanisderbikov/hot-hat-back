package ru.hothat.sabotage.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Право играть с диверсиями.
 *
 * <p>Сегодня это две колонки карточки игрока ({@code sabotage_unlimited},
 * {@code sabotage_games_used}) плюс константа {@code FREE_SABOTAGE_GAMES = 5}
 * в коде. Предел вынесен в колонку: раздать одному человеку десять партий
 * сегодня нельзя иначе как выкаткой.
 *
 * <p>{@link #freeGamesUsed} растёт атомарным
 * {@code UPDATE … SET free_games_used = free_games_used + 1} (§6.4): две
 * вкладки, одновременно начавшие партию с диверсиями, потеряли бы один
 * инкремент и подарили игроку бесплатную партию.
 *
 * <p>Внешний ключ на учётку здесь есть, в отличие от строк партии: это право
 * УЧЁТКИ, а не место за столом, и тест-бота здесь быть не может.
 */
@Entity
@Table(name = "sabotage_entitlement", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class SabotageEntitlement {

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    /** Безлимит: у владельца сервиса и у его друзей. */
    @Builder.Default
    @Column(nullable = false)
    private Boolean unlimited = false;

    @Builder.Default
    @Column(name = "free_games_limit", nullable = false)
    private Integer freeGamesLimit = 5;

    @Builder.Default
    @Column(name = "free_games_used", nullable = false)
    private Integer freeGamesUsed = 0;
}
