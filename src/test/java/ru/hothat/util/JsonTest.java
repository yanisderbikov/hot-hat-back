package ru.hothat.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Защитное чтение JSONB-полей.
 *
 * <p>Смысл этих хелперов в том, что документ, написанный прошлой версией, не
 * должен ронять логику: пропущенное поле читается пустотой, а не исключением.
 */
class JsonTest {

    @Test
    @DisplayName("Не-карта читается пустой картой, а не роняет чтение")
    void nonMapBecomesEmptyMap() {
        assertThat(Json.map(null)).isEmpty();
        assertThat(Json.map("строка")).isEmpty();
        assertThat(Json.map(Map.of("a", 1))).containsEntry("a", 1);
    }

    @Test
    @DisplayName("Не-список читается пустым списком")
    void nonListBecomesEmptyList() {
        assertThat(Json.list(null)).isEmpty();
        assertThat(Json.list("строка")).isEmpty();
        assertThat(Json.list(List.of(1, 2))).containsExactly(1, 2);
    }

    @Test
    @DisplayName("Список строк отбрасывает пустые значения и приводит числа к строкам")
    void stringsDropEmptyAndConvertNumbers() {
        assertThat(Json.strings(List.of("a", "", "b"))).containsExactly("a", "b");
        assertThat(Json.strings(java.util.Arrays.asList("a", null, "b"))).containsExactly("a", "b");
        assertThat(Json.strings(List.of(1, 2))).containsExactly("1", "2");
    }

    @Test
    @DisplayName("Повторы в списке строк отбрасываются, а порядок сохраняется")
    void uniqueStringsKeepFirstOccurrenceOrder() {
        assertThat(Json.uniqueStrings(List.of("b", "a", "b"))).containsExactly("b", "a");
    }

    @Test
    @DisplayName("Из списка вытаскиваются только карты, всё прочее пропускается")
    void mapsSkipNonMapItems() {
        assertThat(Json.maps(java.util.Arrays.asList(Map.of("a", 1), "строка", null, Map.of("b", 2))))
                .hasSize(2);
    }

    @Test
    @DisplayName("Число читается из числа, из строки и из ничего")
    void numbersAreReadDefensively() {
        assertThat(Json.num(7)).isEqualTo(7);
        assertThat(Json.num(7.9)).isEqualTo(7);
        assertThat(Json.num("42")).isEqualTo(42);
        assertThat(Json.num(null)).isZero();
        assertThat(Json.num("не число")).isZero();
        assertThat(Json.num(null, 5)).isEqualTo(5);
        assertThat(Json.num("не число", 5)).isEqualTo(5);
    }

    @Test
    @DisplayName("Дробное число сохраняет точность, а негодное значение уходит в запасное")
    void doublesKeepPrecision() {
        assertThat(Json.dbl(12.5, 0)).isEqualTo(12.5);
        assertThat(Json.dbl("12.5", 0)).isEqualTo(12.5);
        assertThat(Json.dbl(null, 60)).isEqualTo(60);
        assertThat(Json.dbl("нет", 60)).isEqualTo(60);
    }

    @Test
    @DisplayName("Истиной считается только настоящее «да»")
    void onlyRealTruthIsTrue() {
        assertThat(Json.bool(true)).isTrue();
        assertThat(Json.bool("true")).isTrue();
        assertThat(Json.bool(false)).isFalse();
        assertThat(Json.bool(null)).isFalse();
        assertThat(Json.bool(1)).isFalse();
    }

    @Test
    @DisplayName("Строка из ничего пуста, а слишком длинная обрезается")
    void stringsAreSafeAndCapped() {
        assertThat(Json.str(null)).isEmpty();
        assertThat(Json.str(42)).isEqualTo("42");
        assertThat(Json.str("абвгд", 3)).isEqualTo("абв");
        assertThat(Json.str("аб", 3)).isEqualTo("аб");
    }
}
