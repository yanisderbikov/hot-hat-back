package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import ru.hothat.game.domain.MatchEngine;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.domain.TechnicalTermination;
import ru.hothat.game.port.RoomLifecyclePort;
import ru.hothat.game.store.MatchSession;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Сверка состава партии со списком тех, кто на видеосвязи.
 *
 * <p>Общая часть трёх сценариев: обычной сверки, доклада об уходе и доклада о
 * разрыве связи. Различаются они не правилами, а тем, откуда берётся список
 * ушедших и позволено ли закрыть комнату, — поэтому правило написано один раз.
 */
@Component
@RequiredArgsConstructor
class MatchPresenceReconciler {

    /** Сколько ждём вернувшегося игрока в обычной партии. */
    private static final long CASUAL_LIMIT_MS = 90_000;
    /** В рейтинговой ждём дольше: на кону результат сезона. */
    private static final long RANKED_LIMIT_MS = 180_000;
    /** Массовым считается обрыв у половины состава, но не меньше троих. */
    private static final int MASS_FAILURE_MINIMUM = 3;

    private final MatchEngine engine;
    private final RoomLifecyclePort rooms;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    /**
     * @param connected     кто на связи по данным видеосвязи
     * @param mayCloseRoom  позволено ли закрыть комнату, если разошлись все
     */
    Result reconcile(MatchSession session, Set<String> connected, boolean mayCloseRoom) {
        MatchState state = session.state();
        long now = clock.millis();
        if (!state.getPhase().pausable()) {
            return new Result(Outcome.NOT_LIVE, List.of(), null, 0);
        }
        List<String> expected = new ArrayList<>();
        for (String uid : state.allPlayers()) {
            if (!state.isTestBot(uid)) {
                expected.add(uid);
            }
        }
        List<String> missing = expected.stream().filter(uid -> !connected.contains(uid)).toList();
        long limit = state.isRanked() ? RANKED_LIMIT_MS : CASUAL_LIMIT_MS;

        if (missing.isEmpty()) {
            return whenEverybodyIsBack(session, state);
        }
        if (mayCloseRoom && !state.isRanked() && missing.size() == expected.size() && !expected.isEmpty()) {
            // Из обычной партии исчезли разом все: доигрывать её некому.
            events.publishEvent(new MatchEvents.MatchTerminated(state.getRoomId(), state.getGameNumber(),
                    "all_players_disconnected"));
            rooms.closeRoom(state.getRoomId(), "all_players_disconnected");
            return new Result(Outcome.ROOM_CLOSED, missing, null, limit);
        }
        boolean waitedEnough = state.isPaused() && state.getPauseStartedAtMs() > 0
                && now - state.getPauseStartedAtMs() >= limit;
        if (waitedEnough) {
            return terminate(session, state, missing, expected.size(), now, limit);
        }
        engine.pauseForMissing(state, missing, missing.stream().map(state::nameOf).toList());
        return new Result(Outcome.PAUSED, missing, null, limit);
    }

    private Result whenEverybodyIsBack(MatchSession session, MatchState state) {
        if (state.isHostPaused()) {
            // Пауза хозяина переживает сверку: снять её может только он сам.
            engine.pauseByHost(state);
            return new Result(Outcome.PAUSED, List.of(), null, 0);
        }
        if (!state.isPaused()) {
            return new Result(Outcome.RUNNING, List.of(), null, 0);
        }
        engine.resume(state);
        return new Result(Outcome.RESUMED, List.of(), null, 0);
    }

    /**
     * Ожидание кончилось.
     *
     * <p>Массовый обрыв никого не наказывает: когда исчезла половина стола,
     * дело не в человеке, а в связи, и рейтинг такой партии аннулируется.
     */
    private Result terminate(MatchSession session, MatchState state, List<String> missing,
                             int expectedCount, long now, long limit) {
        boolean massFailure = missing.size() >= Math.max(MASS_FAILURE_MINIMUM, (int) Math.ceil(expectedCount / 2.0));
        TechnicalTermination termination = new TechnicalTermination(
                state.isRanked() ? TechnicalTermination.RANKED : TechnicalTermination.CASUAL,
                List.copyOf(missing),
                missing.stream().map(state::nameOf).toList(),
                state.isRanked() && massFailure,
                now);
        engine.terminate(state, termination);
        events.publishEvent(new MatchEvents.MatchTerminated(state.getRoomId(), state.getGameNumber(),
                termination.type()));
        rooms.onMatchFinished(state.getRoomId(), state.getGameNumber(), termination.type());
        return new Result(Outcome.TECHNICALLY_FINISHED, missing, termination, limit);
    }

    /** Исход сверки; каждый сценарий переводит его в своё слово ответа. */
    enum Outcome {
        RUNNING, PAUSED, RESUMED, TECHNICALLY_FINISHED, ROOM_CLOSED, NOT_LIVE
    }

    record Result(Outcome outcome, List<String> missing, TechnicalTermination termination, long limitMs) {
    }
}
