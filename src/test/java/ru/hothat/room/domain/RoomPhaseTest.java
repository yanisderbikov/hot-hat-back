package ru.hothat.room.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Фаза комнаты: она решает, что в комнате сейчас можно делать. */
class RoomPhaseTest {

    @Test
    @DisplayName("Фаза читается по своему слову туда и обратно")
    void wireValueRoundTrips() {
        for (RoomPhase phase : RoomPhase.values()) {
            assertThat(RoomPhase.fromWire(phase.wireValue())).isEqualTo(phase);
        }
    }

    @Test
    @DisplayName("Незнакомое значение колонки читается как набор, а не делает комнату недоступной")
    void unknownWireValueBecomesSetup() {
        assertThat(RoomPhase.fromWire("нечто")).isEqualTo(RoomPhase.SETUP);
        assertThat(RoomPhase.match("нечто")).isEmpty();
        assertThat(RoomPhase.match("resetting")).contains(RoomPhase.RESETTING);
    }

    @Test
    @DisplayName("Набор — единственная фаза, в которой меняют составы, слова и хозяйство")
    void onlySetupIsSetup() {
        assertThat(RoomPhase.SETUP.isSetup()).isTrue();
        assertThat(RoomPhase.RESETTING.isSetup()).isFalse();
        assertThat(RoomPhase.TURN_INTRO.isSetup()).isFalse();
    }

    @Test
    @DisplayName("Доигранная партия живой не считается: после итогов вход открыт так же, как в наборе")
    void finishedIsNotLive() {
        assertThat(RoomPhase.ACTIVE.isLive()).isTrue();
        assertThat(RoomPhase.APPEAL.isLive()).isTrue();
        assertThat(RoomPhase.BETWEEN.isLive()).isTrue();
        assertThat(RoomPhase.TURN_INTRO.isLive()).isTrue();
        assertThat(RoomPhase.FINISHED.isLive()).isFalse();
        assertThat(RoomPhase.CLOSED.isLive()).isFalse();
        assertThat(RoomPhase.SETUP.isLive()).isFalse();
    }
}
