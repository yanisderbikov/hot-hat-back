package ru.hothat.game.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.config.ApiException;
import ru.hothat.support.Matches;
import ru.hothat.support.PredictableRandom;
import ru.hothat.support.TestClock;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static ru.hothat.support.Matches.ANN;

/** Пауза партии: что замораживается и что возвращается после снятия. */
class MatchEnginePauseTest {

    private final TestClock clock = TestClock.start();
    private final MatchEngine engine = new MatchEngine(clock, new PredictableRandom());

    @Test
    @DisplayName("Пауза во время хода замораживает остаток времени и останавливает отсчёт")
    void pauseFreezesRemainingTurnTime() {
        MatchState state = Matches.started(engine, "небо", "море", "лес");
        engine.beginTurn(state, ANN);
        clock.advance(20_000);

        engine.pauseByHost(state);

        assertThat(state.isPaused()).isTrue();
        assertThat(state.isHostPaused()).isTrue();
        assertThat(state.getPausedTurnRemainingMs()).isEqualTo(40_000);
        assertThat(state.getTurnStartedAtMs()).isNull();
        assertThat(TurnRules.deadline(state)).isZero();
    }

    @Test
    @DisplayName("Повторная пауза не перезамораживает остаток: он остаётся от первой")
    void secondPauseKeepsTheFirstRemainder() {
        MatchState state = Matches.started(engine, "небо", "море", "лес");
        engine.beginTurn(state, ANN);
        clock.advance(20_000);
        engine.pauseByHost(state);
        long pausedAt = state.getPauseStartedAtMs();
        clock.advance(10_000);

        engine.pauseForMissing(state, List.of(Matches.CAT), List.of("Катя"));

        assertThat(state.getPausedTurnRemainingMs()).isEqualTo(40_000);
        assertThat(state.getPauseStartedAtMs()).isEqualTo(pausedAt);
    }

    @Test
    @DisplayName("Снятие паузы возвращает ровно замороженный остаток, а не полную длительность")
    void resumeReturnsFrozenRemainder() {
        MatchState state = Matches.started(engine, "небо", "море", "лес");
        engine.beginTurn(state, ANN);
        clock.advance(20_000);
        engine.pauseByHost(state);
        clock.advance(5 * 60_000);

        engine.resume(state);

        assertThat(state.isPaused()).isFalse();
        assertThat(TurnRules.deadline(state)).isEqualTo(clock.millis() + 40_000);
    }

    @Test
    @DisplayName("Повторное снятие паузы не сокращает ход: остаток остаётся прежним")
    void secondResumeDoesNotShortenTurn() {
        MatchState state = Matches.started(engine, "небо", "море", "лес");
        engine.beginTurn(state, ANN);
        clock.advance(20_000);
        engine.pauseByHost(state);
        engine.resume(state);
        long deadlineAfterResume = TurnRules.deadline(state);
        clock.advance(1000);

        engine.resume(state);

        assertThat(TurnRules.deadline(state)).isEqualTo(deadlineAfterResume);
        assertThat(TurnRules.expired(state, clock.millis())).isFalse();
    }

    @Test
    @DisplayName("Во время паузы слово не засчитывается")
    void pausedMatchRefusesGuesses() {
        MatchState state = Matches.started(engine, "небо", "море", "лес");
        TurnStarted started = engine.beginTurn(state, ANN);
        engine.pauseByHost(state);

        ApiException failure = catchThrowableOfType(
                () -> engine.guess(state, started.turnId(), "w1"), ApiException.class);

        assertThat(failure.getCode()).isEqualTo("GAME_PAUSED");
        assertThat(failure.getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("Пауза во время голосования замораживает и его остаток")
    void pauseFreezesAppealTime() {
        MatchState state = Matches.started(engine, "небо", "море", "лес");
        TurnStarted started = engine.beginTurn(state, ANN);
        engine.complete(state, started.turnId());
        clock.advance(4000);

        engine.pauseByHost(state);

        assertThat(state.getPausedAppealRemainingMs()).isEqualTo(TurnRules.APPEAL_MS - 4000);
        assertThat(state.getAppealEndsAt()).isZero();

        engine.resume(state);
        assertThat(state.getAppealEndsAt()).isEqualTo(clock.millis() + TurnRules.APPEAL_MS - 4000);
    }

    @Test
    @DisplayName("Пауза, продержавшаяся дольше голосования, не мешает подвести итоги хода")
    void pauseOutlivingAppealDoesNotBlockSettling() {
        MatchState state = Matches.started(engine, "небо", "море", "лес");
        TurnStarted started = engine.beginTurn(state, ANN);
        engine.complete(state, started.turnId());
        clock.advance(TurnRules.APPEAL_MS);
        engine.pauseByHost(state);

        AppealClosed closed = engine.closeAppeal(state, started.turnId());

        assertThat(closed.outcome()).isEqualTo(AppealClosed.Outcome.SETTLED);
        assertThat(state.getPhase()).isEqualTo(MatchPhase.TURN_INTRO);
        // Игрок так и не вернулся: партия ждёт его дальше, уже на карточке хода.
        assertThat(state.isPaused()).isTrue();
    }

    @Test
    @DisplayName("Пока голосование идёт, пауза не даёт подвести итоги")
    void pausedAppealBlocksSettling() {
        MatchState state = Matches.started(engine, "небо", "море", "лес");
        TurnStarted started = engine.beginTurn(state, ANN);
        engine.complete(state, started.turnId());
        clock.advance(4000);
        engine.pauseByHost(state);
        clock.advance(TurnRules.APPEAL_MS);

        ApiException failure = catchThrowableOfType(
                () -> engine.closeAppeal(state, started.turnId()), ApiException.class);

        assertThat(failure.getCode()).isEqualTo("GAME_PAUSED");
    }

    @Test
    @DisplayName("До начала партии паузе нечего останавливать")
    void pauseIsRefusedBeforeMatch() {
        MatchState state = Matches.room();

        ApiException failure = catchThrowableOfType(() -> engine.pauseByHost(state), ApiException.class);

        assertThat(failure.getCode()).isEqualTo("ROUND_NOT_ACTIVE");
        assertThat(failure.getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("Пауза из-за пропавших игроков помнит, кого ждут")
    void pauseForMissingRemembersWhoIsAway() {
        MatchState state = Matches.started(engine, "небо", "море", "лес");
        engine.beginTurn(state, ANN);

        engine.pauseForMissing(state, List.of(Matches.CAT), List.of("Катя"));

        assertThat(state.getPauseReason()).isEqualTo("player_disconnected");
        assertThat(state.getPauseMissingUids()).containsExactly(Matches.CAT);
        assertThat(state.isHostPaused()).isFalse();
    }
}
