package ru.hothat.game.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.support.Matches;

import static org.assertj.core.api.Assertions.assertThat;

/** Часы хода: длительность, дедлайн и отсрочка последнего слова. */
class TurnRulesTest {

    private static final long START = 1_000_000L;

    private MatchState turnStartedWith(double roomSeconds, Double ownSeconds) {
        MatchState state = Matches.room(roomSeconds);
        state.setTurnStartedAtMs(START);
        state.setTurnDurationSeconds(ownSeconds);
        return state;
    }

    @Test
    @DisplayName("Ход по умолчанию длится минуту, а отсрочка последнего слова — три секунды")
    void ruleValuesAreFixed() {
        // Числа здесь написаны цифрами намеренно: сравнение константы с ней же
        // компилятор сворачивает, и такой тест не упал бы никогда.
        MatchState state = turnStartedWith(0, null);

        assertThat(TurnRules.durationSeconds(state)).isEqualTo(60);
        assertThat(TurnRules.deadline(state)).isEqualTo(START + 60_000);
        assertThat(TurnRules.inLastWordGrace(state, START + 63_000)).isTrue();
        assertThat(TurnRules.expired(state, START + 63_001)).isTrue();
    }

    @Test
    @DisplayName("Длительность хода берётся из настройки комнаты, пока у хода нет своей")
    void roomDurationIsUsedByDefault() {
        assertThat(TurnRules.durationSeconds(turnStartedWith(90, null))).isEqualTo(90);
    }

    @Test
    @DisplayName("Своя длительность хода перебивает настройку комнаты: после паузы она равна остатку")
    void ownDurationWins() {
        assertThat(TurnRules.durationSeconds(turnStartedWith(90, 12.5))).isEqualTo(12.5);
    }

    @Test
    @DisplayName("Нулевая своя длительность не обнуляет ход, а возвращает к настройке комнаты")
    void zeroOwnDurationFallsBack() {
        assertThat(TurnRules.durationSeconds(turnStartedWith(90, 0.0))).isEqualTo(90);
    }

    @Test
    @DisplayName("Комната без настройки длительности играет ходами по шестьдесят секунд")
    void defaultDurationIsSixtySeconds() {
        assertThat(TurnRules.durationSeconds(turnStartedWith(0, null)))
                .isEqualTo(TurnRules.DEFAULT_DURATION_SECONDS);
    }

    @Test
    @DisplayName("Дедлайн считается от начала хода, а не хранится готовым")
    void deadlineIsComputedFromStart() {
        assertThat(TurnRules.deadline(turnStartedWith(90, 12.5))).isEqualTo(START + 12_500);
    }

    @Test
    @DisplayName("У хода без начала читается дедлайн прошлой версии")
    void legacyDeadlineIsUsedWhenTurnHasNoStart() {
        MatchState state = Matches.room(60);
        state.setTurnStartedAtMs(null);
        state.setLegacyTurnEndsAt(START + 4321);

        assertThat(TurnRules.deadline(state)).isEqualTo(START + 4321);
    }

    @Test
    @DisplayName("Когда дедлайна нет вовсе, он равен нулю, а не отрицательному числу")
    void absentDeadlineIsZero() {
        MatchState state = Matches.room(60);
        state.setTurnStartedAtMs(null);
        state.setLegacyTurnEndsAt(-500);

        assertThat(TurnRules.deadline(state)).isZero();
    }

    @Test
    @DisplayName("Остаток хода не уходит в минус после дедлайна")
    void remainingNeverGoesNegative() {
        MatchState state = turnStartedWith(60, null);

        assertThat(TurnRules.remainingMs(state, START + 20_000)).isEqualTo(40_000);
        assertThat(TurnRules.remainingMs(state, START + 90_000)).isZero();
    }

    @Test
    @DisplayName("Ровно в момент дедлайна ход ещё не истёк и отсрочка ещё не началась")
    void deadlineMomentItselfIsStillTheTurn() {
        MatchState state = turnStartedWith(60, null);
        long deadline = TurnRules.deadline(state);

        assertThat(TurnRules.expired(state, deadline)).isFalse();
        assertThat(TurnRules.inLastWordGrace(state, deadline)).isFalse();
    }

    @Test
    @DisplayName("Первая миллисекунда после дедлайна и последняя миллисекунда отсрочки — ещё отсрочка")
    void graceCoversThreeSecondsAfterDeadline() {
        MatchState state = turnStartedWith(60, null);
        long deadline = TurnRules.deadline(state);

        assertThat(TurnRules.inLastWordGrace(state, deadline + 1)).isTrue();
        assertThat(TurnRules.inLastWordGrace(state, deadline + TurnRules.LAST_WORD_GRACE_MS)).isTrue();
        assertThat(TurnRules.expired(state, deadline + TurnRules.LAST_WORD_GRACE_MS)).isFalse();
    }

    @Test
    @DisplayName("Через миллисекунду после отсрочки ход истёк, а отсрочка кончилась")
    void turnExpiresRightAfterGrace() {
        MatchState state = turnStartedWith(60, null);
        long afterGrace = TurnRules.deadline(state) + TurnRules.LAST_WORD_GRACE_MS + 1;

        assertThat(TurnRules.expired(state, afterGrace)).isTrue();
        assertThat(TurnRules.inLastWordGrace(state, afterGrace)).isFalse();
    }

    @Test
    @DisplayName("Эффект длиннее остатка хода в ход не помещается")
    void effectMustFitInRemainingTime() {
        MatchState state = turnStartedWith(60, null);
        long tenSecondsLeft = START + 50_000;

        assertThat(TurnRules.fitsInTurn(state, tenSecondsLeft, 10_000)).isTrue();
        assertThat(TurnRules.fitsInTurn(state, tenSecondsLeft, 10_001)).isFalse();
        assertThat(TurnRules.fitsInTurn(state, tenSecondsLeft, 0)).isTrue();
    }
}
