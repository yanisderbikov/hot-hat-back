package ru.hothat.rating.store;

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
 * Личное положение игрока в таблице сезона.
 *
 * <p>{@link #syncedTeamPoints} — не денормализация, а состояние правила:
 * личные очки растут на разницу командных с прошлой синхронизации. Без него
 * зачёт второй партии начислил бы игроку все командные очки заново. Сегодня
 * это {@code last_team_points}, и имя врало: хранится не «сколько было в
 * прошлый раз», а «докуда уже начислено».
 *
 * <p>Ника и аватара здесь нет: это копии карточки игрока.
 */
@Entity
@Table(name = "season_player_standing", schema = "v2")
@IdClass(SeasonPlayerStandingId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class SeasonPlayerStanding {

    @Id
    @Column(name = "board_id", nullable = false, updatable = false)
    private Long boardId;

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    /** Команда, за которую игрок набрал эти очки; пусто — играл вне команды. */
    @Column(name = "team_id")
    private UUID teamId;

    @Builder.Default
    @Column(nullable = false)
    private Integer points = 0;

    @Builder.Default
    @Column(name = "synced_team_points", nullable = false)
    private Integer syncedTeamPoints = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer games = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer wins = 0;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
