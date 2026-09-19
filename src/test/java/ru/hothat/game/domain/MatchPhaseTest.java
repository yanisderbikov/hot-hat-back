package ru.hothat.game.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Фаза партии: слово в колонке, набор пауз и разбор незнакомых значений. */
class MatchPhaseTest {

    @Test
    @DisplayName("Слово фазы едет наружу неизменным: его знают колонка и фронтенд")
    void wireCodesAreStable() {
        assertThat(MatchPhase.SETUP.code()).isEqualTo("setup");
        assertThat(MatchPhase.TURN_INTRO.code()).isEqualTo("turnIntro");
        assertThat(MatchPhase.ACTIVE.code()).isEqualTo("active");
        assertThat(MatchPhase.APPEAL.code()).isEqualTo("appeal");
        assertThat(MatchPhase.BETWEEN.code()).isEqualTo("between");
        assertThat(MatchPhase.FINISHED.code()).isEqualTo("finished");
        assertThat(MatchPhase.CLOSED.code()).isEqualTo("closed");
    }

    @Test
    @DisplayName("Фаза читается по своему слову туда и обратно")
    void codeRoundTrips() {
        for (MatchPhase phase : MatchPhase.values()) {
            assertThat(MatchPhase.of(phase.code())).isEqualTo(phase);
        }
    }

    @Test
    @DisplayName("Незнакомое слово из колонки становится набором, а не роняет партию")
    void unknownValueBecomesSetup() {
        assertThat(MatchPhase.of("нечто")).isEqualTo(MatchPhase.SETUP);
        assertThat(MatchPhase.of(null)).isEqualTo(MatchPhase.SETUP);
        assertThat(MatchPhase.of("  turnIntro  ")).isEqualTo(MatchPhase.TURN_INTRO);
    }

    @Test
    @DisplayName("Паузу принимают только четыре фазы: до партии нечего останавливать, после — нечего продолжать")
    void onlyLivePhasesArePausable() {
        assertThat(MatchPhase.TURN_INTRO.pausable()).isTrue();
        assertThat(MatchPhase.ACTIVE.pausable()).isTrue();
        assertThat(MatchPhase.APPEAL.pausable()).isTrue();
        assertThat(MatchPhase.BETWEEN.pausable()).isTrue();
        assertThat(MatchPhase.SETUP.pausable()).isFalse();
        assertThat(MatchPhase.FINISHED.pausable()).isFalse();
        assertThat(MatchPhase.CLOSED.pausable()).isFalse();
    }

    @Test
    @DisplayName("Партия идёт в тех же четырёх фазах, в которых её можно поставить на паузу")
    void liveMatchesPausableSet() {
        for (MatchPhase phase : MatchPhase.values()) {
            assertThat(phase.live()).isEqualTo(phase.pausable());
        }
    }
}
