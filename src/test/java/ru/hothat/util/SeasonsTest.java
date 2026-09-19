package ru.hothat.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Сезон рейтинга: он считается по UTC и рвётся не там, где год. */
class SeasonsTest {

    private static Seasons.Season at(int year, int month, int day, int hour) {
        return Seasons.at(ZonedDateTime.of(year, month, day, hour, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Каждый месяц попадает в свой сезон")
    void everyMonthHasItsSeason() {
        assertThat(at(2026, 1, 15, 12).season()).isEqualTo("winter");
        assertThat(at(2026, 2, 15, 12).season()).isEqualTo("winter");
        assertThat(at(2026, 3, 1, 12).season()).isEqualTo("spring");
        assertThat(at(2026, 5, 31, 12).season()).isEqualTo("spring");
        assertThat(at(2026, 6, 1, 12).season()).isEqualTo("summer");
        assertThat(at(2026, 8, 31, 12).season()).isEqualTo("summer");
        assertThat(at(2026, 9, 1, 12).season()).isEqualTo("autumn");
        assertThat(at(2026, 11, 30, 12).season()).isEqualTo("autumn");
        assertThat(at(2026, 12, 1, 12).season()).isEqualTo("winter");
    }

    @Test
    @DisplayName("Зима рвётся по календарному году: декабрь и январь — разные сезоны рейтинга")
    void winterIsSplitByTheYearBoundary() {
        Seasons.Season lastMomentOfYear = at(2026, 12, 31, 23);
        Seasons.Season firstMomentOfYear = at(2027, 1, 1, 0);

        assertThat(lastMomentOfYear).isEqualTo(new Seasons.Season(2026, "winter"));
        assertThat(firstMomentOfYear).isEqualTo(new Seasons.Season(2027, "winter"));
        assertThat(Seasons.rankingId(lastMomentOfYear.year(), lastMomentOfYear.season(), "classic", "ru"))
                .isNotEqualTo(Seasons.rankingId(firstMomentOfYear.year(), firstMomentOfYear.season(), "classic", "ru"));
    }

    @Test
    @DisplayName("Сезон считается по UTC, а не по часовому поясу вызывающего")
    void seasonIsComputedInUtc() {
        // Первое марта на Камчатке уже наступило, а по UTC — ещё февраль.
        ZonedDateTime kamchatkaMorning = ZonedDateTime.of(2026, 3, 1, 9, 0, 0, 0, ZoneOffset.ofHours(12));

        assertThat(Seasons.at(kamchatkaMorning.withZoneSameInstant(ZoneOffset.UTC)).season())
                .isEqualTo("winter");
        // Тот же миг в поясе игрока дал бы другой сезон — поэтому пояс выбирает
        // не вызывающий: now() всегда передаёт UTC.
        assertThat(Seasons.at(kamchatkaMorning).season()).isEqualTo("spring");
    }

    @Test
    @DisplayName("Каждый сезон известен по имени")
    void everySeasonIsNamed() {
        for (int month = 1; month <= 12; month++) {
            assertThat(Seasons.NAMES).contains(at(2026, month, 15, 12).season());
        }
    }

    @Test
    @DisplayName("Идентификатор таблицы рейтинга собирается из года, сезона, режима и дивизиона")
    void rankingIdIsBuiltFromAllFour() {
        assertThat(Seasons.rankingId(2026, "summer", "sabotage", "de"))
                .isEqualTo("2026-summer-sabotage-de");
    }

    @Test
    @DisplayName("Незнакомый дивизион в идентификаторе рейтинга становится русским")
    void unknownDivisionFallsBackToRussian() {
        assertThat(Seasons.rankingId(2026, "summer", "classic", "xx"))
                .isEqualTo("2026-summer-classic-ru");
    }

    @Test
    @DisplayName("Незнакомый режим читается как классический")
    void unknownModeIsClassic() {
        assertThat(Seasons.mode("sabotage")).isEqualTo("sabotage");
        assertThat(Seasons.mode("нечто")).isEqualTo("classic");
        assertThat(Seasons.mode(null)).isEqualTo("classic");
    }

    @Test
    @DisplayName("Текущий сезон считается тем же правилом, что и любой названный момент")
    void nowUsesTheSameRule() {
        assertThat(Seasons.now()).isEqualTo(Seasons.at(ZonedDateTime.now(ZoneOffset.UTC)));
    }
}
