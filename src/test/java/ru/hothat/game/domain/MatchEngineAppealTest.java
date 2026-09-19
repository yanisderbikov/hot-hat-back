package ru.hothat.game.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.config.ApiException;
import ru.hothat.support.Matches;
import ru.hothat.support.PredictableRandom;
import ru.hothat.support.TestClock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static ru.hothat.support.Matches.ANN;
import static ru.hothat.support.Matches.BOB;
import static ru.hothat.support.Matches.CAT;
import static ru.hothat.support.Matches.DAN;
import static ru.hothat.support.Matches.RED;

/** Голосование по спорным словам и подведение итогов хода. */
class MatchEngineAppealTest {

    private final TestClock clock = TestClock.start();
    private final MatchEngine engine = new MatchEngine(clock, new PredictableRandom());

    /** Ход красных на три угаданных слова; голосование открыто. */
    private MatchState turnWithThreeGuesses() {
        MatchState state = Matches.started(engine, "небо", "море", "лес", "дом", "сад");
        TurnStarted started = engine.beginTurn(state, ANN);
        engine.guess(state, started.turnId(), "w1");
        engine.guess(state, started.turnId(), "w2");
        engine.guess(state, started.turnId(), "w3");
        engine.complete(state, started.turnId());
        return state;
    }

    private String turnId(MatchState state) {
        return state.getLastTurn().turnId();
    }

    @Test
    @DisplayName("Судить собственный ход нельзя: голос игрока разбираемой команды отклоняется")
    void judgedTeamCannotVote() {
        MatchState state = turnWithThreeGuesses();

        ApiException failure = catchThrowableOfType(
                () -> engine.vote(state, ANN, "w1", true), ApiException.class);

        assertThat(failure.getCode()).isEqualTo("APPEAL_NOT_ELIGIBLE");
        assertThat(failure.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Голос соперника принимается и показывает расклад: сколько подано и сколько нужно")
    void opponentVoteShowsTally() {
        MatchState state = turnWithThreeGuesses();

        AppealVoteCast cast = engine.vote(state, CAT, "w1", true);

        assertThat(cast.myVotes()).containsExactly("w1");
        assertThat(cast.eligibleCount()).isEqualTo(2);
        assertThat(cast.votesForWord()).isEqualTo(1);
        assertThat(cast.majority()).isEqualTo(2);
    }

    @Test
    @DisplayName("Голос за слово не из этого хода отклоняется")
    void unknownWordIsRefused() {
        MatchState state = turnWithThreeGuesses();

        ApiException failure = catchThrowableOfType(
                () -> engine.vote(state, CAT, "w-чужое", true), ApiException.class);

        assertThat(failure.getCode()).isEqualTo("WORD_NOT_IN_TURN");
        assertThat(failure.getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("Пропущенное слово в голосование не попадает: отменять там нечего")
    void skippedWordIsNotOnTheBallot() {
        MatchState state = Matches.started(engine, "небо", "море", "лес", "дом", "сад");
        TurnStarted started = engine.beginTurn(state, ANN);
        engine.skip(state, started.turnId(), "w-skip");
        engine.guess(state, started.turnId(), "w1");
        engine.complete(state, started.turnId());

        ApiException failure = catchThrowableOfType(
                () -> engine.vote(state, CAT, "w-skip", true), ApiException.class);

        assertThat(failure.getCode()).isEqualTo("WORD_NOT_IN_TURN");
        assertThat(state.getLastTurn().guessedWords()).extracting(GuessedWord::id).containsExactly("w1");
    }

    @Test
    @DisplayName("После истечения времени голосования голос не принимается")
    void lateVoteIsRefused() {
        MatchState state = turnWithThreeGuesses();
        clock.advance(TurnRules.APPEAL_MS + 1);

        ApiException failure = catchThrowableOfType(
                () -> engine.vote(state, CAT, "w1", true), ApiException.class);

        assertThat(failure.getCode()).isEqualTo("APPEAL_CLOSED");
    }

    @Test
    @DisplayName("Одного голоса из двух возможных не хватает: слово остаётся засчитанным")
    void singleVoteIsNotMajority() {
        MatchState state = turnWithThreeGuesses();
        engine.vote(state, CAT, "w1", true);
        clock.advance(TurnRules.APPEAL_MS);

        AppealClosed closed = engine.closeAppeal(state, turnId(state));

        assertThat(closed.outcome()).isEqualTo(AppealClosed.Outcome.SETTLED);
        assertThat(closed.invalidWordIds()).isEmpty();
        assertThat(closed.finalScore()).isEqualTo(3);
        assertThat(closed.teamScoreDelta()).isZero();
    }

    @Test
    @DisplayName("Большинство отменяет слово: счёт падает, слово возвращается в шляпу")
    void majorityCancelsWord() {
        MatchState state = turnWithThreeGuesses();
        int wordsBefore = state.getWordsLeft();
        engine.vote(state, CAT, "w1", true);
        engine.vote(state, DAN, "w1", true);
        clock.advance(TurnRules.APPEAL_MS);

        AppealClosed closed = engine.closeAppeal(state, turnId(state));

        assertThat(closed.invalidWordIds()).containsExactly("w1");
        assertThat(closed.returnedWords()).containsExactly("небо");
        assertThat(closed.finalScore()).isEqualTo(2);
        assertThat(closed.teamScoreDelta()).isEqualTo(-1);
        assertThat(state.getBag()).contains("небо");
        assertThat(state.getWordsLeft()).isEqualTo(wordsBefore + 1);
    }

    @Test
    @DisplayName("Снятый голос не считается: слово остаётся засчитанным")
    void withdrawnVoteIsNotCounted() {
        MatchState state = turnWithThreeGuesses();
        engine.vote(state, CAT, "w1", true);
        engine.vote(state, DAN, "w1", true);
        engine.vote(state, DAN, "w1", false);
        clock.advance(TurnRules.APPEAL_MS);

        AppealClosed closed = engine.closeAppeal(state, turnId(state));

        assertThat(closed.invalidWordIds()).isEmpty();
        assertThat(closed.finalScore()).isEqualTo(3);
    }

    @Test
    @DisplayName("Пока время голосования не вышло, итоги не подводятся")
    void appealStaysOpenUntilDeadline() {
        MatchState state = turnWithThreeGuesses();
        clock.advance(TurnRules.APPEAL_MS - 1);

        AppealClosed closed = engine.closeAppeal(state, turnId(state));

        assertThat(closed.outcome()).isEqualTo(AppealClosed.Outcome.STILL_OPEN);
        assertThat(state.getPhase()).isEqualTo(MatchPhase.APPEAL);
    }

    @Test
    @DisplayName("Повторное подведение итогов ничего не начисляет и не возвращает слова дважды")
    void settlingTwiceChangesNothing() {
        MatchState state = turnWithThreeGuesses();
        engine.vote(state, CAT, "w1", true);
        engine.vote(state, DAN, "w1", true);
        clock.advance(TurnRules.APPEAL_MS);
        AppealClosed first = engine.closeAppeal(state, turnId(state));
        int wordsAfterFirst = state.getWordsLeft();

        AppealClosed again = engine.closeAppeal(state, first.turnId());

        assertThat(again.outcome()).isEqualTo(AppealClosed.Outcome.ALREADY_SETTLED);
        assertThat(again.finalScore()).isEqualTo(first.finalScore());
        assertThat(state.getWordsLeft()).isEqualTo(wordsAfterFirst);
    }

    @Test
    @DisplayName("Итоги чужого хода подвести нельзя")
    void staleTurnIdIsRefused() {
        MatchState state = turnWithThreeGuesses();
        clock.advance(TurnRules.APPEAL_MS);

        ApiException failure = catchThrowableOfType(
                () -> engine.closeAppeal(state, "turn_deadbeefdeadbeef"), ApiException.class);

        assertThat(failure.getCode()).isEqualTo("TURN_STALE");
        assertThat(failure.getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("Награды за ход считаются по итоговому счёту, а не по счёту до отмены слов")
    void rewardsFollowFinalScore() {
        MatchState state = turnWithThreeGuesses();
        engine.vote(state, CAT, "w1", true);
        engine.vote(state, DAN, "w1", true);
        clock.advance(TurnRules.APPEAL_MS);

        AppealClosed closed = engine.closeAppeal(state, turnId(state));

        // Счёт 2, а не 3: помидоры дают за каждые два слова, мемы — за каждые три.
        assertThat(closed.finalScore()).isEqualTo(2);
        assertThat(closed.rewards()).containsEntry("tomato", 2).containsEntry("meme", 0);
    }

    @Test
    @DisplayName("Редкая награда за четвёртое слово команды достаётся первому по составу")
    void specialRewardGoesToFirstInRoster() {
        MatchState state = Matches.started(engine, "небо", "море", "лес", "дом", "сад", "кот");
        TurnStarted started = engine.beginTurn(state, ANN);
        engine.guess(state, started.turnId(), "w1");
        engine.guess(state, started.turnId(), "w2");
        engine.guess(state, started.turnId(), "w3");
        engine.guess(state, started.turnId(), "w4");
        engine.complete(state, started.turnId());
        clock.advance(TurnRules.APPEAL_MS);

        AppealClosed closed = engine.closeAppeal(state, turnId(state));

        assertThat(closed.finalScore()).isEqualTo(4);
        assertThat(closed.specialRewardsByUid()).containsOnlyKeys(ANN);
        assertThat(closed.specialRewardsByUid().get(ANN)).containsEntry("negative", 1);
        assertThat(closed.rewardedUids()).containsExactly(ANN, BOB);
        assertThat(state.getSpecialCursorByTeam()).containsEntry(RED, 1);
    }

    @Test
    @DisplayName("Пустая шляпа после итогов заканчивает партию")
    void emptyBagFinishesMatch() {
        MatchState state = Matches.started(engine, "небо", "море");
        TurnStarted started = engine.beginTurn(state, ANN);
        engine.guess(state, started.turnId(), "w1");
        engine.guess(state, started.turnId(), "w2");
        clock.advance(TurnRules.APPEAL_MS);

        AppealClosed closed = engine.closeAppeal(state, turnId(state));

        assertThat(closed.matchFinished()).isTrue();
        assertThat(state.getPhase()).isEqualTo(MatchPhase.FINISHED);
        assertThat(state.getCurrentTeamId()).isEqualTo(RED);
    }
}
