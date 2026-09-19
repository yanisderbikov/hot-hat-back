package ru.hothat.game.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Перемешивание партии заданным жребием.
 *
 * <p>Смысл {@link RandomSource} — в воспроизводимости: если жребий назначен,
 * перестановка обязана быть предсказуемой до элемента, иначе «слово выпало»
 * проверить нечем.
 */
class RandomSourceTest {

    /** Жребий по написанному: каждый вызов забирает следующее число списка. */
    private static RandomSource scripted(int... values) {
        Deque<Integer> queue = new ArrayDeque<>();
        for (int value : values) {
            queue.add(value);
        }
        return bound -> queue.isEmpty() ? 0 : queue.poll();
    }

    @Test
    @DisplayName("Перемешивание заданным жребием даёт в точности предсказанную перестановку")
    void shuffleFollowsTheScript() {
        // Фишер — Йетс идёт с конца: [абвг] → меняем 3-й с 0-м → [гбва],
        // затем 2-й с 1-м → [гвба], затем 1-й сам с собой → [гвба].
        List<String> shuffled = scripted(0, 1, 1).shuffle(List.of("а", "б", "в", "г"));

        assertThat(shuffled).containsExactly("г", "в", "б", "а");
    }

    @Test
    @DisplayName("Жребий, всегда указывающий на последний элемент, порядок не меняет")
    void identityScriptKeepsOrder() {
        RandomSource lastAlways = bound -> bound - 1;

        assertThat(lastAlways.shuffle(List.of("а", "б", "в"))).containsExactly("а", "б", "в");
    }

    @Test
    @DisplayName("Перемешивание не портит исходный список и сохраняет его состав")
    void shuffleKeepsSourceIntact() {
        List<String> source = List.of("а", "б", "в", "г", "д");

        List<String> shuffled = scripted(2, 0, 1, 0).shuffle(source);

        assertThat(source).containsExactly("а", "б", "в", "г", "д");
        assertThat(shuffled).containsExactlyInAnyOrderElementsOf(source);
    }

    @Test
    @DisplayName("Пустой и одиночный список перемешивать нечем")
    void degenerateListsSurviveShuffle() {
        RandomSource random = scripted();

        assertThat(random.shuffle(List.<String>of())).isEmpty();
        assertThat(random.shuffle(List.of("одно"))).containsExactly("одно");
    }

    @Test
    @DisplayName("Идентификаторы хода, диверсии и клипа строятся тем же жребием и имеют свою длину")
    void turnIdsAreBuiltFromTheSameSource() {
        RandomSource lastAlways = bound -> bound - 1;

        assertThat(TurnIds.turn(lastAlways)).isEqualTo("turn_" + "f".repeat(16));
        assertThat(TurnIds.sabotage(lastAlways)).isEqualTo("sab_" + "f".repeat(20));
        assertThat(TurnIds.clip(lastAlways)).isEqualTo("repl_" + "f".repeat(20));
    }

    @Test
    @DisplayName("Разный жребий даёт разные идентификаторы ходов")
    void differentDrawsGiveDifferentTurnIds() {
        RandomSource first = bound -> 0;
        RandomSource second = bound -> 1;

        assertThat(TurnIds.turn(first)).isNotEqualTo(TurnIds.turn(second));
    }
}
