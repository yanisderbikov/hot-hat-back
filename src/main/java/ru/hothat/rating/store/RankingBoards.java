package ru.hothat.rating.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Дверь в {@code v2.ranking_board}; за пределы области рейтинга не выходит.
 *
 * <p>Доска опознаётся четырьмя полями, а не склеенной строкой
 * «2026-winter-sabotage-ru»: склейку собирали в трёх местах, и разъехаться
 * им ничто не мешало.
 */
@Repository
interface RankingBoards extends JpaRepository<RankingBoard, Long> {

    Optional<RankingBoard> findByYearAndSeasonAndModeAndDivisionLanguage(
            Integer year, String season, String mode, String divisionLanguage);

    /**
     * Завести доску, если её ещё нет.
     *
     * <p>Вставка с {@code on conflict do nothing} вместо «прочитал — не нашёл —
     * вставил»: первый зачёт сезона и первый взгляд на таблицу могут прийтись
     * на один миг, и проверка перед вставкой отдала бы 500 на уникальном ключе.
     */
    @Modifying
    @Query(value = "insert into v2.ranking_board (year, season, mode, division_language) "
            + "values (:year, :season, :mode, :division) on conflict do nothing", nativeQuery = true)
    void insertIfAbsent(@Param("year") Integer year, @Param("season") String season,
                        @Param("mode") String mode, @Param("division") String division);
}
