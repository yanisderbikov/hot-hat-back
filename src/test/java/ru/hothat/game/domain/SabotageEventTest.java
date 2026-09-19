package ru.hothat.game.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Кому видно событие диверсии: выстрел — всем, съёмка Подмены — двоим.
 *
 * <p>Правило проверяется на записи события, потому что им пользуются сразу
 * два пути доставки — кадр канала и пакет LiveKit. Разойдись они, третье лицо
 * узнало бы о съёмке одним путём, пока другой её прячет.
 */
class SabotageEventTest {

    private static SabotageEvent event(String type) {
        return new SabotageEvent("sab-1", type, null, "clip-1", "turn-1", "attacker", "Аня", "target",
                1_000L, 10_000L, 1, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("Выстрел видят все, включая того, кто в событии не назван, и адресатов у него нет")
    void shotIsPublic() {
        SabotageEvent tomato = event("tomato");

        assertThat(tomato.visibleTo("attacker")).isTrue();
        assertThat(tomato.visibleTo("target")).isTrue();
        assertThat(tomato.visibleTo("bystander")).isTrue();
        assertThat(tomato.visibleTo(null)).as("зритель без места на сцене видит выстрел").isTrue();
        assertThat(tomato.audience()).as("пусто — всей комнате").isEmpty();
    }

    @Test
    @DisplayName("Съёмку Подмены видят только снимающий и снимаемый — и только им она адресована")
    void recordingIsForTwo() {
        SabotageEvent recording = event(SabotageEvent.RECORD_TYPE);

        assertThat(recording.visibleTo("attacker")).isTrue();
        assertThat(recording.visibleTo("target")).isTrue();
        assertThat(recording.visibleTo("bystander")).isFalse();
        assertThat(recording.visibleTo(null)).isFalse();
        assertThat(recording.audience()).containsExactly("attacker", "target");
    }

    @Test
    @DisplayName("Съёмка без названного снимаемого адресуется одному снимающему, а не всей комнате")
    void recordingWithoutTargetStaysPrivate() {
        SabotageEvent recording = event(SabotageEvent.RECORD_TYPE).withTarget(null);

        assertThat(recording.audience()).containsExactly("attacker");
        assertThat(recording.visibleTo("bystander")).isFalse();
    }
}
