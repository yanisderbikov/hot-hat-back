package ru.hothat.admin.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Отрезок дней, за который админ смотрит статистику.
 *
 * <p>Правило переехало сюда из {@code AdminController:48-76} — двадцати восьми
 * строк разбора диапазона в контроллере (F13). Разбор дат контроллеру запрещён
 * (§7.2), но дело не только в слое: «пустой диапазон — неделя», «перевёрнутый
 * разворачиваем», «слишком длинный обрезаем» — это три доменных решения, а
 * веб-слой должен уметь ровно одно, превратить {@code 2026-09-01} в дату.
 * Заодно правило стало проверяемым без поднятого приложения: класс не знает ни
 * про Spring, ни про базу и берёт время только из {@link Clock} (§7.2).
 *
 * <p>Разбор строки сюда не переехал намеренно. Раньше нечитаемое значение
 * молча подменялось запасным: администратор просил июль, получал неделю и не
 * узнавал об этом. Теперь формат — дело bean-валидации и конвертера
 * {@link LocalDate}, а непонятная дата — это 400, а не тихая подмена.
 *
 * <p>Границы дня считаются в UTC, как и раньше: снимки, аналитика и ключи
 * {@code usage_daily} пишутся по UTC, и сдвиг в часовой пояс администратора
 * развёл бы «день» в отчёте и «день» в таблице.
 */
public final class DateRange {

    /** Столько показываем, когда диапазон не задан вовсе. */
    public static final Duration DEFAULT_SPAN = Duration.ofDays(7);

    /**
     * Потолок длины отрезка. Десять лет — тот же порог, что стоял в
     * контроллере: он не про здравый смысл, а про предел сканирования событий,
     * за которым запрос перестаёт укладываться в таймаут.
     */
    public static final Duration MAX_SPAN = Duration.ofDays(3650);

    private final Instant startAt;
    private final Instant endAt;

    private DateRange(Instant startAt, Instant endAt) {
        this.startAt = startAt;
        this.endAt = endAt;
    }

    /**
     * Диапазон для дашборда: незаданные концы заполняются сами.
     *
     * <p>Ни одна граница не обязательна, потому что первый заход на экран
     * админки происходит до того, как человек что-то выбрал.
     *
     * @param start первый день включительно; {@code null} — неделя назад от конца
     * @param end   последний день включительно; {@code null} — сейчас
     */
    public static DateRange lastWeekByDefault(LocalDate start, LocalDate end, Clock clock) {
        Instant now = clock.instant();
        Instant endAt = end == null ? now : endOfDay(end);
        Instant startAt = start == null ? endAt.minus(DEFAULT_SPAN) : startOfDay(start);
        return clamp(startAt, endAt);
    }

    /**
     * Диапазон, заданный целиком, — или ничего.
     *
     * <p>Половина отрезка бессмысленна для истории снимков: «с 1 июля» без
     * второго конца хранилище всё равно не умеет, и подставлять недостающую
     * границу самому значило бы отвечать не на тот вопрос. Поэтому здесь нет
     * умолчаний: либо обе даты, либо весь список последних дней.
     */
    public static Optional<DateRange> explicit(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return Optional.empty();
        }
        return Optional.of(clamp(startOfDay(start), endOfDay(end)));
    }

    /** Перевёрнутый разворачиваем, слишком длинный обрезаем с начала. */
    private static DateRange clamp(Instant start, Instant end) {
        Instant from = start;
        Instant to = end;
        if (from.isAfter(to)) {
            Instant swap = from;
            from = to;
            to = swap;
        }
        if (Duration.between(from, to).compareTo(MAX_SPAN) > 0) {
            from = to.minus(MAX_SPAN);
        }
        return new DateRange(from, to);
    }

    private static Instant startOfDay(LocalDate day) {
        return day.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    /**
     * Конец дня — 23:59:59.999, а не полночь следующего: иначе события ровно в
     * полночь попадали бы сразу в два соседних отчёта.
     */
    private static Instant endOfDay(LocalDate day) {
        return day.atTime(23, 59, 59, 999_000_000).toInstant(ZoneOffset.UTC);
    }

    public Instant startAt() {
        return startAt;
    }

    public Instant endAt() {
        return endAt;
    }

    /** Первый день отрезка. Границы дня считаются в UTC — см. пояснение к классу. */
    public LocalDate start() {
        return startAt.atZone(ZoneOffset.UTC).toLocalDate();
    }

    /** Последний день отрезка, включительно. */
    public LocalDate end() {
        return endAt.atZone(ZoneOffset.UTC).toLocalDate();
    }

    /** Тот же день строкой — в таком виде он едет в ответ администратору. */
    public String startDay() {
        return start().toString();
    }

    public String endDay() {
        return end().toString();
    }
}
