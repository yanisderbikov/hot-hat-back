package ru.hothat.lobby.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Кого с кем сводит подбор.
 *
 * <p>Правила перенесены из {@code MatchmakingServiceImpl} без изменений и
 * собраны здесь по одной причине: до сих пор они были вплетены в тот же метод,
 * который писал комнату, и проверить их можно было только поднятым
 * приложением с базой. Здесь они принимают признаки комнаты и отвечают
 * выбором — ни базы, ни Spring.
 *
 * <p>Порядок сортировки разный у двух видов подбора, и это существенно.
 * Рейтинговая пара ищет равного соперника: первой идёт близость рейтинга, и
 * только при равной близости — заполненность. Быстрая игра равного соперника
 * не ищет вовсе, ей важно начать: первой идёт заполненность, потому что
 * комната, которой не хватает одного, соберётся сейчас, а пустая — через две
 * минуты или никогда.
 */
public final class TicketMatching {

    /** Столько ищется комната, прежде чем заявка признаётся несостоявшейся. */
    public static final long SEARCH_WINDOW_MS = 120_000;

    /** Разница в очках, больше которой рейтинговые пары не сводятся. */
    public static final int RATING_SPREAD = 400;

    /** Размеры комнаты, которые бывают: по двое в команде. */
    public static final Set<Integer> ROOM_SIZES = Set.of(4, 6, 8, 10);

    /** Размер по умолчанию и он же запасной для непонятного запроса. */
    public static final int DEFAULT_SIZE = 10;

    private TicketMatching() {
    }

    /**
     * Размер комнаты.
     *
     * <p>Непонятное значение молча становится десяткой — так вёл себя прежний
     * движок, и менять это здесь нельзя: контракт уже закрыл набор значений
     * перечислением, а сюда приходит только то, что через него прошло.
     */
    public static int normalizeSize(int requested) {
        return ROOM_SIZES.contains(requested) ? requested : DEFAULT_SIZE;
    }

    /**
     * Комната подбора глазами отбора.
     *
     * <p>Своя запись, а не запись порта комнаты: правило не должно знать, что
     * у комнаты сто три колонки и что вместимость лежит в двух из них.
     */
    public record Option(String roomId,
                         boolean ranked,
                         boolean privateRoom,
                         boolean managed,
                         boolean closed,
                         String gameMode,
                         int maxPlayers,
                         String divisionLanguage,
                         String matchmakingLanguage,
                         List<String> applicantUids,
                         List<String> rankedTeamIds,
                         long deadlineMs,
                         int ratingTarget) {
    }

    /**
     * Чего хочет подающий заявку.
     *
     * @param memberUids     кто подаёт: один игрок или двое напарников
     * @param rankedTeamId   идентификатор пары; {@code null} у быстрой игры
     * @param teamRating     очки пары в текущем сезоне; 0 у быстрой игры
     */
    public record Wanted(boolean ranked,
                         String gameMode,
                         int maxPlayers,
                         String gameLanguage,
                         String divisionLanguage,
                         List<String> memberUids,
                         String rankedTeamId,
                         int teamRating) {
    }

    /**
     * Комната, в которой заявка уже стоит.
     *
     * <p>Ищется раньше свободных и отменяет их выбор целиком. Иначе очередной
     * опрос уводил бы игрока в другую комнату, а первая продолжала бы ждать
     * его до конца срока — ровно это и делал прежний движок, потому что опрос
     * был повторной подачей заявки.
     */
    public static Optional<Option> alreadyStanding(List<Option> options, Wanted wanted) {
        return options.stream()
                .filter(option -> sameSetup(option, wanted))
                .filter(option -> wanted.memberUids().stream().anyMatch(option.applicantUids()::contains))
                .findFirst();
    }

    /** Лучшая из свободных комнат; пусто — подходящей нет, нужно заводить свою. */
    public static Optional<Option> bestFit(List<Option> options, Wanted wanted, long nowMs) {
        Comparator<Option> order = wanted.ranked()
                ? Comparator.<Option>comparingInt(option -> ratingDistance(option, wanted))
                        .thenComparing(Comparator.comparingInt((Option option) -> option.applicantUids().size()).reversed())
                : Comparator.comparingInt((Option option) -> option.applicantUids().size()).reversed();
        return options.stream()
                .filter(option -> fits(option, wanted, nowMs))
                .min(order);
    }

    /** Разница в очках между парой и комнатой; у быстрой игры её нет вовсе. */
    public static int ratingDistance(Option option, Wanted wanted) {
        return wanted.ranked() ? Math.abs(option.ratingTarget() - wanted.teamRating()) : 0;
    }

    /**
     * Та же настройка комнаты: вид подбора, режим, размер и язык.
     *
     * <p>Язык сравнивается по строке подбора, а не по дивизиону: быстрая
     * международная комната собирается на общем английском, и её дивизион с
     * языком партии намеренно не совпадает.
     */
    private static boolean sameSetup(Option option, Wanted wanted) {
        return option.managed()
                && !option.closed()
                && option.ranked() == wanted.ranked()
                && wanted.gameMode().equals(option.gameMode())
                && option.maxPlayers() == wanted.maxPlayers()
                && option.matchmakingLanguage().equals(wanted.gameLanguage());
    }

    /**
     * Свободна ли комната для этой заявки.
     *
     * <p>Порядок условий значения не имеет — все они обязательны, — но каждое
     * отсекает свой случай: приватную комнату подбор не трогает вовсе, чужой
     * дивизион рейтинговую пару не примет, истёкшая комната уже никого не
     * дождётся, а пара, которая туда не помещается целиком, разлучилась бы.
     */
    private static boolean fits(Option option, Wanted wanted, long nowMs) {
        if (!sameSetup(option, wanted) || option.privateRoom()) {
            return false;
        }
        if (wanted.ranked() && !option.divisionLanguage().equals(wanted.divisionLanguage())) {
            return false;
        }
        if (option.deadlineMs() <= nowMs) {
            return false;
        }
        if (option.applicantUids().size() + wanted.memberUids().size() > wanted.maxPlayers()) {
            return false;
        }
        if (wanted.ranked() && option.rankedTeamIds().contains(wanted.rankedTeamId())) {
            return false;
        }
        return !wanted.ranked() || ratingDistance(option, wanted) <= RATING_SPREAD;
    }
}
