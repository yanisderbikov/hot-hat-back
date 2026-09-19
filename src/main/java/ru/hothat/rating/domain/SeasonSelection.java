package ru.hothat.rating.domain;

import ru.hothat.util.Divisions;
import ru.hothat.util.Seasons;

/**
 * Какую именно таблицу сезона спросили: год, сезон, режим, дивизион.
 *
 * <p>Раньше эти четыре умолчания жили внутри {@code RatingServiceImpl.ratings}
 * и наружу не объявлялись: клиент присылал что угодно, движок молча подменял
 * непонятное значение и отвечал таблицей другого дивизиона. План требует
 * объявить умолчания явно, поэтому разбор вынесен сюда, в одно место, — и три
 * адреса таблиц выбирают сезон одинаково.
 *
 * <p>Чистое правило: часы сюда не заглядывают. Текущий сезон приходит
 * аргументом, иначе «какая сейчас таблица» стало бы невозможно проверить
 * тестом, не переводя системное время.
 */
public record SeasonSelection(int year, String season, String mode, String divisionLanguage) {

    /**
     * Режим по умолчанию — диверсии: это основной режим продукта, и именно
     * его подставлял старый движок. У {@code Seasons.mode()} запасной другой
     * ({@code classic}), поэтому общий помощник здесь не годится.
     */
    public static final String DEFAULT_MODE = "sabotage";

    /** Дивизион по умолчанию — тот же, что у {@code Divisions.normalize}. */
    public static final String DEFAULT_DIVISION = "ru";

    /** Рейтинги начались в 2026-м: раньше этого года таблиц не существует. */
    public static final int MIN_YEAR = 2026;
    public static final int MAX_YEAR = 2100;

    /**
     * Собирает выбор из того, что прислал клиент, и текущего сезона.
     *
     * <p>Любое поле может быть не задано — тогда берётся умолчание. Значения
     * сверяются с теми же наборами, которыми пользуется движок
     * ({@code Seasons.NAMES}, {@code Seasons.MODES}, {@code Divisions.CODES}),
     * второй копии списков здесь нет.
     */
    public static SeasonSelection resolve(String season, Integer year, String mode, String division,
                                          int currentYear, String currentSeason) {
        int requestedYear = year == null ? currentYear : year;
        // Проверка на null отдельно от набора: NAMES и MODES собраны Set.of,
        // а такой набор на contains(null) бросает NPE — «параметр не задан»
        // уронило бы запрос вместо того, чтобы взять умолчание.
        return new SeasonSelection(
                Math.max(MIN_YEAR, Math.min(MAX_YEAR, requestedYear)),
                season != null && Seasons.NAMES.contains(season) ? season : currentSeason,
                mode != null && Seasons.MODES.contains(mode) ? mode : DEFAULT_MODE,
                Divisions.normalize(division, DEFAULT_DIVISION));
    }
}
