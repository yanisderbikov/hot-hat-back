package ru.hothat.game.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hothat.config.ApiException;
import ru.hothat.support.Matches;
import ru.hothat.support.PredictableRandom;
import ru.hothat.support.TestClock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static ru.hothat.support.Matches.ANN;
import static ru.hothat.support.Matches.BLUE;
import static ru.hothat.support.Matches.BOB;
import static ru.hothat.support.Matches.CAT;
import static ru.hothat.support.Matches.RED;

/** Ход партии: кто объясняет, что происходит со шляпой и когда ход кончается. */
class MatchEngineTurnTest {

    private final TestClock clock = TestClock.start();
    private final MatchEngine engine = new MatchEngine(clock, new PredictableRandom());

    @Nested
    @DisplayName("Начало партии")
    class Start {

        @Test
        @DisplayName("Старт партии ставит на ход первую команду очереди и наполняет шляпу")
        void firstTeamGoesFirst() {
            MatchState state = Matches.started(engine, "небо", "море", "лес");

            assertThat(state.getPhase()).isEqualTo(MatchPhase.TURN_INTRO);
            assertThat(state.getCurrentTeamId()).isEqualTo(RED);
            assertThat(state.getCurrentTeamIndex()).isZero();
            assertThat(state.getWordsLeft()).isEqualTo(3);
            assertThat(state.getBag()).containsExactlyInAnyOrder("небо", "море", "лес");
            assertThat(state.getGameNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("Старт замораживает составы и имена: вышедший игрок остаётся в протоколе")
        void rostersAndNamesAreFrozen() {
            MatchState state = Matches.started(engine, "небо");

            assertThat(state.rosterOf(RED)).containsExactly(ANN, BOB);
            assertThat(state.allPlayers()).containsExactly(ANN, BOB, CAT, Matches.DAN);
            assertThat(state.nameOf(ANN)).isEqualTo("Аня");
        }
    }

    @Nested
    @DisplayName("Начало хода")
    class BeginTurn {

        @Test
        @DisplayName("Начало хода назначает угадывающим напарника по команде и заводит часы")
        void explainerGetsPartnerAsGuesser() {
            MatchState state = Matches.started(engine, "небо", "море");

            TurnStarted started = engine.beginTurn(state, ANN);

            assertThat(started.outcome()).isEqualTo(TurnStarted.Outcome.STARTED);
            assertThat(state.getExplainerUid()).isEqualTo(ANN);
            assertThat(state.getGuesserUid()).isEqualTo(BOB);
            assertThat(state.getGuesserName()).isEqualTo("Боря");
            assertThat(state.getPhase()).isEqualTo(MatchPhase.ACTIVE);
            assertThat(started.word()).isEqualTo("небо");
            assertThat(started.deadlineMs()).isEqualTo(clock.millis() + 60_000);
        }

        @Test
        @DisplayName("Начать ход за чужую команду нельзя")
        void otherTeamCannotBegin() {
            MatchState state = Matches.started(engine, "небо");

            ApiException failure = catchThrowableOfType(() -> engine.beginTurn(state, CAT), ApiException.class);

            assertThat(failure.getCode()).isEqualTo("TURN_NOT_YOURS");
            assertThat(failure.getStatus()).isEqualTo(403);
        }

        @Test
        @DisplayName("Повтор начала хода при потере связи не тянет второе слово")
        void repeatedBeginKeepsSameWord() {
            MatchState state = Matches.started(engine, "небо", "море");
            TurnStarted first = engine.beginTurn(state, ANN);

            TurnStarted again = engine.beginTurn(state, ANN);

            assertThat(again.outcome()).isEqualTo(TurnStarted.Outcome.ALREADY_RUNNING);
            assertThat(again.word()).isEqualTo(first.word());
            assertThat(state.getBag()).containsExactly("море");
        }

        @Test
        @DisplayName("Напарник не может перехватить уже идущий ход")
        void partnerCannotStealRunningTurn() {
            MatchState state = Matches.started(engine, "небо", "море");
            engine.beginTurn(state, ANN);

            ApiException failure = catchThrowableOfType(() -> engine.beginTurn(state, BOB), ApiException.class);

            assertThat(failure.getCode()).isEqualTo("TURN_ALREADY_STARTED");
            assertThat(failure.getStatus()).isEqualTo(409);
        }
    }

    @Nested
    @DisplayName("Слова хода")
    class Words {

        @Test
        @DisplayName("Угаданное слово даёт команде очко и достаёт из шляпы следующее")
        void guessScoresAndDrawsNext() {
            MatchState state = Matches.started(engine, "небо", "море", "лес");
            TurnStarted started = engine.beginTurn(state, ANN);

            WordResolved resolved = engine.guess(state, started.turnId(), "w1");

            assertThat(resolved.outcome()).isEqualTo(WordResolved.Outcome.COUNTED);
            assertThat(resolved.word()).isEqualTo("небо");
            assertThat(resolved.nextWord()).isEqualTo("море");
            assertThat(resolved.teamScoreDelta()).isEqualTo(1);
            assertThat(state.getCurrentTurnScore()).isEqualTo(1);
            assertThat(state.getWordsLeft()).isEqualTo(2);
            assertThat(resolved.turnClosed()).isFalse();
        }

        @Test
        @DisplayName("Повтор того же нажатия при потере связи не даёт второго очка")
        void repeatedGuessScoresOnce() {
            MatchState state = Matches.started(engine, "небо", "море", "лес");
            TurnStarted started = engine.beginTurn(state, ANN);
            engine.guess(state, started.turnId(), "w1");

            WordResolved again = engine.guess(state, started.turnId(), "w1");

            assertThat(again.outcome()).isEqualTo(WordResolved.Outcome.REPEATED);
            assertThat(again.teamScoreDelta()).isZero();
            assertThat(state.getCurrentTurnScore()).isEqualTo(1);
        }

        @Test
        @DisplayName("Пропущенное слово возвращается в шляпу и очка не приносит")
        void skipReturnsWordToBag() {
            MatchState state = Matches.started(engine, "небо", "море", "лес");
            TurnStarted started = engine.beginTurn(state, ANN);

            WordResolved resolved = engine.skip(state, started.turnId(), "w1");

            assertThat(resolved.outcome()).isEqualTo(WordResolved.Outcome.SKIPPED);
            assertThat(state.getCurrentTurnScore()).isZero();
            assertThat(state.getWordsLeft()).isEqualTo(3);
            assertThat(state.getBag()).contains("небо");
        }

        @Test
        @DisplayName("Нажатие с идентификатором чужого хода отклоняется")
        void staleTurnIdIsRefused() {
            MatchState state = Matches.started(engine, "небо", "море");
            engine.beginTurn(state, ANN);

            ApiException failure = catchThrowableOfType(
                    () -> engine.guess(state, "turn_deadbeefdeadbeef", "w1"), ApiException.class);

            assertThat(failure.getCode()).isEqualTo("TURN_STALE");
            assertThat(failure.getStatus()).isEqualTo(409);
        }

        @Test
        @DisplayName("Слово, нажатое в трёхсекундной отсрочке, засчитывается и закрывает ход")
        void lastWordInGraceIsCounted() {
            MatchState state = Matches.started(engine, "небо", "море", "лес");
            TurnStarted started = engine.beginTurn(state, ANN);
            clock.advance(60_000 + TurnRules.LAST_WORD_GRACE_MS - 1);

            WordResolved resolved = engine.guess(state, started.turnId(), "w1");

            assertThat(resolved.outcome()).isEqualTo(WordResolved.Outcome.COUNTED);
            assertThat(resolved.turnClosed()).isTrue();
            assertThat(resolved.nextWord()).isNull();
            assertThat(state.getCurrentTurnScore()).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(MatchPhase.APPEAL);
        }

        @Test
        @DisplayName("После отсрочки нажатие опоздало: очка нет, ход закрыт")
        void guessAfterGraceIsRefused() {
            MatchState state = Matches.started(engine, "небо", "море", "лес");
            TurnStarted started = engine.beginTurn(state, ANN);
            clock.advance(60_000 + TurnRules.LAST_WORD_GRACE_MS + 1);

            WordResolved resolved = engine.guess(state, started.turnId(), "w1");

            assertThat(resolved.outcome()).isEqualTo(WordResolved.Outcome.TURN_EXPIRED);
            assertThat(resolved.turnClosed()).isTrue();
            assertThat(state.getCurrentTurnScore()).isZero();
            assertThat(state.getPhase()).isEqualTo(MatchPhase.APPEAL);
        }

        @Test
        @DisplayName("Шляпа пустеет ровно один раз: последнее слово закрывает ход само")
        void emptyBagClosesTurn() {
            MatchState state = Matches.started(engine, "небо", "море");
            TurnStarted started = engine.beginTurn(state, ANN);
            engine.guess(state, started.turnId(), "w1");

            WordResolved last = engine.guess(state, started.turnId(), "w2");

            assertThat(last.outcome()).isEqualTo(WordResolved.Outcome.COUNTED);
            assertThat(last.turnClosed()).isTrue();
            assertThat(state.getWordsLeft()).isZero();
            assertThat(state.getBag()).isEmpty();
            assertThat(state.getPhase()).isEqualTo(MatchPhase.APPEAL);
        }
    }

    @Nested
    @DisplayName("Конец хода")
    class EndOfTurn {

        @Test
        @DisplayName("Завершение хода возвращает недоигранное слово в шляпу и открывает голосование")
        void completeReturnsCurrentWord() {
            MatchState state = Matches.started(engine, "небо", "море", "лес");
            TurnStarted started = engine.beginTurn(state, ANN);

            TurnClosing closing = engine.complete(state, started.turnId());

            assertThat(closing.outcome()).isEqualTo(TurnClosing.Outcome.CLOSED);
            assertThat(state.getBag()).containsExactlyInAnyOrder("небо", "море", "лес");
            assertThat(state.getWordsLeft()).isEqualTo(3);
            assertThat(state.getPhase()).isEqualTo(MatchPhase.APPEAL);
            // Десять секунд цифрами: константу компилятор подставил бы в обе стороны.
            assertThat(closing.appealEndsAt()).isEqualTo(clock.millis() + 10_000);
        }

        @Test
        @DisplayName("Повторное завершение хода не открывает второе голосование")
        void completeIsIdempotent() {
            MatchState state = Matches.started(engine, "небо", "море");
            TurnStarted started = engine.beginTurn(state, ANN);
            TurnClosing first = engine.complete(state, started.turnId());
            clock.advance(1000);

            TurnClosing again = engine.complete(state, started.turnId());

            assertThat(again.outcome()).isEqualTo(TurnClosing.Outcome.ALREADY_CLOSED);
            assertThat(state.getAppealEndsAt()).isEqualTo(first.appealEndsAt());
        }

        @Test
        @DisplayName("Закрыть ход по часам раньше срока нельзя")
        void expireBeforeDeadlineDoesNothing() {
            MatchState state = Matches.started(engine, "небо", "море");
            TurnStarted started = engine.beginTurn(state, ANN);
            clock.advance(30_000);

            TurnClosing closing = engine.expire(state, started.turnId());

            assertThat(closing.outcome()).isEqualTo(TurnClosing.Outcome.NOT_YET);
            assertThat(state.getPhase()).isEqualTo(MatchPhase.ACTIVE);
        }

        @Test
        @DisplayName("Истёкший ход закрывает по серверным часам любой участник")
        void expiredTurnIsClosedByAnyone() {
            MatchState state = Matches.started(engine, "небо", "море");
            TurnStarted started = engine.beginTurn(state, ANN);
            clock.advance(60_000 + TurnRules.LAST_WORD_GRACE_MS + 1);

            TurnClosing closing = engine.expire(state, started.turnId());

            assertThat(closing.outcome()).isEqualTo(TurnClosing.Outcome.CLOSED);
            assertThat(state.getPhase()).isEqualTo(MatchPhase.APPEAL);
        }
    }

    @Nested
    @DisplayName("Очередь команд")
    class Order {

        @Test
        @DisplayName("После итогов хода очередь переходит второй команде")
        void queuePassesToNextTeam() {
            MatchState state = Matches.started(engine, "небо", "море", "лес");
            TurnStarted started = engine.beginTurn(state, ANN);
            engine.complete(state, started.turnId());
            clock.advance(TurnRules.APPEAL_MS);

            engine.closeAppeal(state, started.turnId());

            assertThat(state.getCurrentTeamId()).isEqualTo(BLUE);
            assertThat(state.getCurrentTeamIndex()).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(MatchPhase.TURN_INTRO);
        }

        @Test
        @DisplayName("Очередь ходит по кругу: после последней команды снова первая")
        void queueWrapsAround() {
            MatchState state = Matches.started(engine, "небо", "море", "лес", "дом");
            playWholeTurn(state, ANN);
            playWholeTurn(state, CAT);

            assertThat(state.getCurrentTeamId()).isEqualTo(RED);
            assertThat(state.getCurrentTeamIndex()).isZero();
        }

        @Test
        @DisplayName("Повторная передача очереди после итогов ничего не меняет")
        void advanceAfterSettlingIsNoop() {
            MatchState state = Matches.started(engine, "небо", "море", "лес");
            TurnStarted started = engine.beginTurn(state, ANN);
            engine.complete(state, started.turnId());
            clock.advance(TurnRules.APPEAL_MS);
            engine.closeAppeal(state, started.turnId());

            TurnAdvanced advanced = engine.advance(state, started.turnId());

            assertThat(advanced.outcome()).isEqualTo(TurnAdvanced.Outcome.ALREADY_ADVANCED);
            assertThat(state.getCurrentTeamId()).isEqualTo(BLUE);
        }

        private void playWholeTurn(MatchState state, String explainerUid) {
            TurnStarted started = engine.beginTurn(state, explainerUid);
            engine.complete(state, started.turnId());
            clock.advance(TurnRules.APPEAL_MS);
            engine.closeAppeal(state, started.turnId());
        }
    }
}
