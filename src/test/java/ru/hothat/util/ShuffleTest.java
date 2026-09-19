package ru.hothat.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Перемешивание на криптостойком генераторе.
 *
 * <p>Источник случайности здесь свой и наружу не выведен, поэтому проверяется
 * не конкретная перестановка, а то, что обязано выполняться при любом жребии:
 * состав сохранён, исходный список не испорчен, каждый элемент достижим.
 * Перестановку по заданному жребию проверяет {@code RandomSourceTest}.
 */
class ShuffleTest {

    private static final List<String> WORDS = List.of("а", "б", "в", "г", "д", "е");

    @Test
    @DisplayName("Перемешивание сохраняет состав и не портит исходный список")
    void shuffleKeepsEveryElement() {
        List<String> shuffled = Shuffle.of(WORDS);

        assertThat(shuffled).containsExactlyInAnyOrderElementsOf(WORDS);
        assertThat(WORDS).containsExactly("а", "б", "в", "г", "д", "е");
    }

    @Test
    @DisplayName("Вырожденные списки перемешивание переживают")
    void degenerateListsSurvive() {
        assertThat(Shuffle.of(List.<String>of())).isEmpty();
        assertThat(Shuffle.of(List.of("одно"))).containsExactly("одно");
    }

    @Test
    @DisplayName("Перемешивание действительно меняет порядок, а не возвращает копию")
    void shuffleActuallyReorders() {
        boolean reorderedAtLeastOnce = false;
        for (int attempt = 0; attempt < 50 && !reorderedAtLeastOnce; attempt++) {
            reorderedAtLeastOnce = !Shuffle.of(WORDS).equals(WORDS);
        }

        // Совпасть 50 раз подряд на шести элементах — один шанс из 720^50.
        assertThat(reorderedAtLeastOnce).isTrue();
    }

    @Test
    @DisplayName("Взятое слово исчезает из мешка, а из пустого мешка не берётся ничего")
    void takeRemovesTheElement() {
        List<String> bag = new ArrayList<>(WORDS);

        String taken = Shuffle.take(bag);

        assertThat(WORDS).contains(taken);
        assertThat(bag).hasSize(WORDS.size() - 1).doesNotContain(taken);
        assertThat(Shuffle.take(new ArrayList<String>())).isNull();
    }

    @Test
    @DisplayName("Мешок вычерпывается до дна ровно один раз")
    void bagIsEmptiedExactlyOnce() {
        List<String> bag = new ArrayList<>(WORDS);
        List<String> drawn = new ArrayList<>();

        // Ограничитель, а не while: если слово перестанет уходить из мешка,
        // тест обязан покраснеть, а не крутиться вечно.
        for (int attempt = 0; attempt < WORDS.size() && !bag.isEmpty(); attempt++) {
            drawn.add(Shuffle.take(bag));
        }

        assertThat(bag).isEmpty();
        assertThat(drawn).containsExactlyInAnyOrderElementsOf(WORDS);
        assertThat(Shuffle.take(bag)).isNull();
    }

    @Test
    @DisplayName("Выбранное слово остаётся в мешке, а из пустого не выбирается ничего")
    void pickKeepsTheElement() {
        List<String> bag = new ArrayList<>(WORDS);

        String picked = Shuffle.pick(bag);

        assertThat(bag).containsExactlyElementsOf(WORDS);
        assertThat(WORDS).contains(picked);
        assertThat(Shuffle.pick(new ArrayList<String>())).isNull();
    }

    @Test
    @DisplayName("За много попыток на первом месте успевает побывать каждый элемент")
    void everyElementCanComeFirst() {
        List<String> seenFirst = new ArrayList<>();
        for (int attempt = 0; attempt < 2000; attempt++) {
            String first = Shuffle.of(WORDS).get(0);
            if (!seenFirst.contains(first)) {
                seenFirst.add(first);
            }
        }

        assertThat(seenFirst).containsExactlyInAnyOrderElementsOf(WORDS);
    }
}
