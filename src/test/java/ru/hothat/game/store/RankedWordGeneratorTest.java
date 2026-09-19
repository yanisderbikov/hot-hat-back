package ru.hothat.game.store;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Наборы слов рейтинговой партии: сравнение с историей и запреты.
 *
 * <p>Контекст Spring не поднимается: словари лежат в ресурсах сборки, и
 * генератор читает их сам — ему нужен только вызов загрузки.
 */
class RankedWordGeneratorTest {

    private static RankedWordGenerator generator;

    @BeforeAll
    static void loadDictionaries() {
        generator = new RankedWordGenerator();
        generator.load();
    }

    @Test
    @DisplayName("Ключ сравнения не различает регистр, лишние пробелы и ё с е")
    void normalizeIgnoresCaseSpacesAndYo() {
        assertThat(generator.normalize("  Ёлка   Зелёная ", "ru")).isEqualTo("елка зеленая");
        assertThat(generator.normalize("ЁЖ", "ru")).isEqualTo(generator.normalize("еж", "ru"));
    }

    @Test
    @DisplayName("Ё превращается в е только в русском: у других дивизионов свои буквы")
    void yoIsCollapsedOnlyForRussian() {
        assertThat(generator.normalize("Ёж", "de")).isEqualTo("ёж");
    }

    @Test
    @DisplayName("Пустое слово даёт пустой ключ, а не строку из пробелов")
    void blankWordGivesBlankKey() {
        assertThat(generator.normalize("   ", "ru")).isEmpty();
        assertThat(generator.normalize(null, "ru")).isEmpty();
    }

    @Test
    @DisplayName("Набор приходит нужной длины и без повторов внутри себя")
    void generatedSetHasTheAskedSizeAndNoRepeats() {
        List<String> words = generator.generate(30, List.of(), "ru");

        assertThat(words).hasSize(30);
        assertThat(words.stream().map(word -> generator.normalize(word, "ru")).distinct().toList())
                .hasSize(30);
    }

    @Test
    @DisplayName("Слово, которое игрок уже объяснял, второй раз ему не выпадает")
    void historyOfThePlayerBlocksTheWord() {
        List<String> first = generator.generate(40, List.of(), "ru");
        List<String> history = new ArrayList<>(first);

        List<String> second = generator.generate(40, List.of(history), "ru");

        assertThat(second.stream().map(word -> generator.normalize(word, "ru")))
                .doesNotContainAnyElementsOf(history.stream()
                        .map(word -> generator.normalize(word, "ru")).toList());
    }

    @Test
    @DisplayName("История одного игрока запрещает слово всему составу: слышат его все")
    void historyOfOnePlayerBlocksTheWordForEveryone() {
        List<String> blocked = generator.generate(25, List.of(), "ru");

        List<String> issued = generator.generate(25,
                List.of(List.of(), blocked, List.of()), "ru");

        assertThat(issued.stream().map(word -> generator.normalize(word, "ru")))
                .doesNotContainAnyElementsOf(blocked.stream()
                        .map(word -> generator.normalize(word, "ru")).toList());
    }

    @Test
    @DisplayName("Набор смешивает форматы: одиночные слова, словосочетания и фразы")
    void setMixesWordFormats() {
        List<String> words = generator.generate(20, List.of(), "ru");

        assertThat(words).anyMatch(word -> parts(word) == 1);
        assertThat(words).anyMatch(word -> parts(word) == 2);
        assertThat(words).anyMatch(word -> parts(word) >= 3);
    }

    @Test
    @DisplayName("Неизвестный дивизион не оставляет партию без слов: берётся английский пул")
    void unknownDivisionFallsBackToEnglish() {
        assertThat(generator.generate(10, List.of(), "эльфийский")).hasSize(10);
    }

    @Test
    @DisplayName("Запрос длиннее словаря не зацикливается и отдаёт потолок в сто слов")
    void hugeRequestIsCapped() {
        assertThat(generator.generate(1000, List.of(), "ru")).hasSize(100);
    }

    private static int parts(String word) {
        return word.trim().split("\\s+").length;
    }
}
