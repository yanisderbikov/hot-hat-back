package ru.hothat.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Нормализация дивизиона и выбор языка интерфейса. */
class DivisionsTest {

    @Test
    @DisplayName("Код дивизиона чистится от регистра и пробелов")
    void codeIsNormalized() {
        assertThat(Divisions.normalize("  DE  ")).isEqualTo("de");
        assertThat(Divisions.normalize("kk")).isEqualTo("kk");
    }

    @Test
    @DisplayName("Незнакомый и пустой код становится русским дивизионом")
    void unknownCodeFallsBackToRussian() {
        assertThat(Divisions.normalize("xx")).isEqualTo("ru");
        assertThat(Divisions.normalize(null)).isEqualTo("ru");
        assertThat(Divisions.normalize("")).isEqualTo("ru");
    }

    @Test
    @DisplayName("Запасной дивизион можно назвать своим")
    void fallbackIsConfigurable() {
        assertThat(Divisions.normalize("xx", "en")).isEqualTo("en");
        assertThat(Divisions.normalize("fr", "en")).isEqualTo("fr");
    }

    @Test
    @DisplayName("У каждого дивизиона есть все свои описания, а код совпадает с ключом")
    void everyDivisionIsComplete() {
        assertThat(Divisions.CODES).hasSize(9);
        Divisions.ALL.forEach((code, division) -> {
            assertThat(division.code()).isEqualTo(code);
            assertThat(division.flag()).isNotBlank();
            assertThat(division.locale()).startsWith(code);
            assertThat(division.name()).isNotBlank();
            assertThat(division.divisionName()).isNotBlank();
        });
    }

    @Test
    @DisplayName("Интерфейс переключается только на свой дивизион или на английский")
    void uiLanguageIsLimitedToOwnOrEnglish() {
        assertThat(Divisions.allowedUiLanguage("de", "de")).isEqualTo("de");
        assertThat(Divisions.allowedUiLanguage("de", "en")).isEqualTo("en");
        assertThat(Divisions.allowedUiLanguage("de", "fr")).isEqualTo("de");
        assertThat(Divisions.allowedUiLanguage("de", null)).isEqualTo("de");
    }

    @Test
    @DisplayName("Английский дивизион остаётся на английском, что бы ни просили")
    void englishDivisionStaysEnglish() {
        assertThat(Divisions.allowedUiLanguage("en", "ja")).isEqualTo("en");
        assertThat(Divisions.allowedUiLanguage("en", "en")).isEqualTo("en");
    }

    @Test
    @DisplayName("Значок показывает дивизион у команды и язык у одиночки")
    void badgeDependsOnHavingTeam() {
        assertThat(Divisions.badge("it", true)).isEqualTo("🇮🇹 Divisione Italiana");
        assertThat(Divisions.badge("it", false)).isEqualTo("🇮🇹 Italiano");
    }

    @Test
    @DisplayName("Страна подсказывает дивизион, а незнакомая — английский")
    void countrySuggestsDivision() {
        assertThat(Divisions.suggestedFromCountry("ru")).isEqualTo("ru");
        assertThat(Divisions.suggestedFromCountry("BY")).isEqualTo("ru");
        assertThat(Divisions.suggestedFromCountry("KZ")).isEqualTo("kk");
        assertThat(Divisions.suggestedFromCountry("  ch  ")).isEqualTo("de");
        assertThat(Divisions.suggestedFromCountry("MX")).isEqualTo("es");
        assertThat(Divisions.suggestedFromCountry("TW")).isEqualTo("zh");
        assertThat(Divisions.suggestedFromCountry("ZZ")).isEqualTo("en");
        assertThat(Divisions.suggestedFromCountry("")).isEqualTo("en");
        assertThat(Divisions.suggestedFromCountry(null)).isEqualTo("en");
    }

    @Test
    @DisplayName("Каждая подсказанная по стране запись — существующий дивизион")
    void everySuggestionIsAKnownDivision() {
        for (String country : new String[]{"RU", "KZ", "DE", "ES", "FR", "IT", "CN", "JP", "US", "ZZ"}) {
            assertThat(Divisions.CODES).contains(Divisions.suggestedFromCountry(country));
        }
    }
}
