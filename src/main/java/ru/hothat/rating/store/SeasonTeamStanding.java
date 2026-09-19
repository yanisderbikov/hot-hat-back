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
 * Положение команды в таблице сезона.
 *
 * <p>Ни имени, ни логотипа, ни состава: сегодня строка рейтинга тащит их
 * копиями и они устаревают в тот же миг, когда команда меняет имя. Отдаёт их
 * сама команда.
 *
 * <p>{@code last_technical_penalty} не переехал: он выводится из числа
 * технических поражений (15 → 30 → 50) и его никто не читает.
 *
 * <p>{@code @Version} здесь намеренно нет: очки прибавляются атомарным
 * {@code UPDATE … SET points = points + ?}, значение в память не читается, и
 * два одновременных зачёта не могут потерять друг друга.
 *
 * <p>Внешнего ключа на команду нет намеренно: таблица сезона — исторический
 * факт, и она обязана пережить роспуск команды. Сегодня отказ напарника
 * удаляет команду целиком.
 */
@Entity
@Table(name = "season_team_standing", schema = "v2")
@IdClass(SeasonTeamStandingId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class SeasonTeamStanding {

    @Id
    @Column(name = "board_id", nullable = false, updatable = false)
    private Long boardId;

    @Id
    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Builder.Default
    @Column(nullable = false)
    private Integer points = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer games = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer wins = 0;

    @Builder.Default
    @Column(name = "technical_forfeits", nullable = false)
    private Integer technicalForfeits = 0;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
