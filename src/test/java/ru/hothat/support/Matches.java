package ru.hothat.support;

import ru.hothat.game.domain.MatchEngine;
import ru.hothat.game.domain.MatchState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Готовая партия на две пары: этого состава хватает всем правилам хода.
 *
 * <p>Две команды по двое — наименьшая партия, в которой есть и очередь ходов,
 * и напарник у объясняющего, и соперник с правом голоса в апелляции.
 */
public final class Matches {

    public static final String RED = "team-red";
    public static final String BLUE = "team-blue";

    public static final String ANN = "uid-ann";
    public static final String BOB = "uid-bob";
    public static final String CAT = "uid-cat";
    public static final String DAN = "uid-dan";

    private Matches() {
    }

    /** Комната обычной игры: не рейтинговая, не тестовая, ход шестьдесят секунд. */
    public static MatchState room() {
        return new MatchState("hat-0123456789abcdef", false, false, false, Set.of(), 60);
    }

    public static MatchState room(double turnSeconds) {
        return new MatchState("hat-0123456789abcdef", false, false, false, Set.of(), turnSeconds);
    }

    /** Партия, начатая названными словами; ход у красных, объясняет Аня. */
    public static MatchState started(MatchEngine engine, String... words) {
        MatchState state = room();
        start(engine, state, List.of(words));
        return state;
    }

    public static void start(MatchEngine engine, MatchState state, List<String> words) {
        engine.start(state, List.of(RED, BLUE), rosters(), names(), words);
    }

    public static Map<String, List<String>> rosters() {
        Map<String, List<String>> rosters = new LinkedHashMap<>();
        rosters.put(RED, List.of(ANN, BOB));
        rosters.put(BLUE, List.of(CAT, DAN));
        return rosters;
    }

    public static Map<String, String> names() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put(ANN, "Аня");
        names.put(BOB, "Боря");
        names.put(CAT, "Катя");
        names.put(DAN, "Даня");
        return names;
    }
}
