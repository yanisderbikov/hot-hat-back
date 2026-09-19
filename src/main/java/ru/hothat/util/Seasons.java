package ru.hothat.util;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Set;

/** Сезон рейтинга считается по UTC — так же, как nowSeason() в portal.js. */
public final class Seasons {

    public static final Set<String> NAMES = Set.of("winter", "spring", "summer", "autumn");
    public static final Set<String> MODES = Set.of("classic", "sabotage");

    public record Season(int year, String season) {
    }

    private Seasons() {
    }

    public static Season now() {
        return at(ZonedDateTime.now(ZoneOffset.UTC));
    }

    /**
     * Тот же расчёт для названного момента.
     *
     * <p>Вынесен из {@link #now()} затем, что границы сезонов и года —
     * первое, что стоит проверить, а по системным часам их не проверить
     * никак: декабрьский рубеж наступает раз в году.
     */
    public static Season at(ZonedDateTime moment) {
        int m = moment.getMonthValue();
        String season = (m == 12 || m <= 2) ? "winter" : m <= 5 ? "spring" : m <= 8 ? "summer" : "autumn";
        return new Season(moment.getYear(), season);
    }

    public static String rankingId(int year, String season, String mode, String division) {
        return year + "-" + season + "-" + mode + "-" + Divisions.normalize(division);
    }

    public static String mode(Object value) {
        String v = Json.str(value);
        return MODES.contains(v) ? v : "classic";
    }
}
