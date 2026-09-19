package ru.hothat.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Транслитерация и ник по умолчанию. */
class LatinTest {

    @Test
    @DisplayName("Кириллица переводится в латиницу, а регистр первой буквы сохраняется")
    void cyrillicBecomesLatin() {
        assertThat(Latin.latinize("Ярослав")).isEqualTo("Yaroslav");
        assertThat(Latin.latinize("щука")).isEqualTo("schuka");
        assertThat(Latin.latinize("Щука")).isEqualTo("Schuka");
    }

    @Test
    @DisplayName("Твёрдый и мягкий знаки исчезают, а латиница и цифры остаются как есть")
    void signsDisappearAndLatinStays() {
        assertThat(Latin.latinize("подъезд")).isEqualTo("podezd");
        assertThat(Latin.latinize("соль")).isEqualTo("sol");
        assertThat(Latin.latinize("Ann 42")).isEqualTo("Ann 42");
        assertThat(Latin.latinize(null)).isEmpty();
    }

    @Test
    @DisplayName("Ник по умолчанию строится из имени латиницей")
    void nicknameIsBuiltFromName() {
        assertThat(Latin.nicknameBase("Ярослав", "uid-123456")).isEqualTo("Yaroslav");
    }

    @Test
    @DisplayName("Ник не начинается с цифры: перед ней встаёт буква")
    void nicknameNeverStartsWithDigit() {
        assertThat(Latin.nicknameBase("42", "uid-123456")).isEqualTo("p42");
    }

    @Test
    @DisplayName("Из имени, от которого ничего не осталось, ник строится по хвосту идентификатора")
    void emptyNameFallsBackToUidTail() {
        assertThat(Latin.nicknameBase("ъь", "uid-abcdef")).isEqualTo("playerabcdef");
        assertThat(Latin.nicknameBase("", "uid-abcdef")).isEqualTo("playerabcdef");
    }

    @Test
    @DisplayName("Ник обрезается до шестнадцати знаков")
    void nicknameIsCappedAtSixteen() {
        assertThat(Latin.nicknameBase("Александрович-Иванов", "uid-abcdef")).hasSize(16);
    }

    @Test
    @DisplayName("Пробелы и знаки препинания из ника выбрасываются")
    void punctuationIsDropped() {
        assertThat(Latin.nicknameBase("Анна Мария!", "uid-abcdef")).isEqualTo("AnnaMariya");
    }

    @Test
    @DisplayName("Хвост идентификатора — последние шесть знаков, а короткий берётся целиком")
    void tailIsLastSixCharacters() {
        assertThat(Latin.tail("uid-abcdef")).isEqualTo("abcdef");
        assertThat(Latin.tail("abc")).isEqualTo("abc");
        assertThat(Latin.tail(null)).isEmpty();
    }
}
