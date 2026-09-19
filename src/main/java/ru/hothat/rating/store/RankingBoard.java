package ru.hothat.rating.store;

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
 * Таблица сезона: год, сезон, режим, дивизион — и её чемпион.
 *
 * <p>Сегодня доска опознаётся склеенной строкой «2026-winter-sabotage-ru», и
 * собирают её в трёх местах. Четыре поля стали четырьмя колонками с общей
 * уникальностью, а ссылаются на доску суррогатом: он короче двадцати символов
 * склейки и не разъедется с полями, из которых она собиралась.
 *
 * <p>Чемпион — снимок, а не ссылка на команду: сезон закончился, команда
 * могла смениться именем или распасться, а «чемпион 2026-winter» обязан
 * пережить её роспуск. Логотипа среди колонок нет намеренно: он весит до
 * 280 КБ и берётся у команды, пока она существует.
 */
@Entity
@Table(name = "ranking_board", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RankingBoard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "board_id")
    private Long boardId;

    @Column(nullable = false, updatable = false)
    private Integer year;

    /** winter | spring | summer | autumn — набор закрыт ограничением базы. */
    @Column(nullable = false, updatable = false, length = 8)
    private String season;

    /** classic | sabotage — набор закрыт ограничением базы. */
    @Column(nullable = false, updatable = false, length = 16)
    private String mode;

    @Column(name = "division_language", nullable = false, updatable = false, length = 8)
    private String divisionLanguage;

    @Column(name = "champion_team_id")
    private UUID championTeamId;

    @Column(name = "champion_name", length = 30)
    private String championName;

    @Column(name = "champion_crowned_at")
    private Instant championCrownedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
