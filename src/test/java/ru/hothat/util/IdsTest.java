package ru.hothat.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.config.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Форматы идентификаторов, на которые опирается фронтенд. */
class IdsTest {

    @Test
    @DisplayName("Свежий идентификатор комнаты всегда проходит собственную проверку формата")
    void freshRoomIdMatchesItsOwnPattern() {
        for (int i = 0; i < 200; i++) {
            String id = Ids.newRoomId();
            assertThat(id).matches(Ids.ROOM.pattern());
            assertThat(Ids.requireRoomId(id)).isEqualTo(id);
        }
    }

    @Test
    @DisplayName("Идентификатор комнаты приводится к нижнему регистру и очищается от пробелов")
    void roomIdIsTrimmedAndLowercased() {
        assertThat(Ids.requireRoomId("  HAT-0123456789ABCDEF  ")).isEqualTo("hat-0123456789abcdef");
    }

    @Test
    @DisplayName("Слишком короткий, слишком длинный и не шестнадцатеричный идентификаторы отклоняются")
    void malformedRoomIdIsRefused() {
        for (String bad : new String[]{"hat-0123456789abcde", "hat-0123456789abcdef0", "hat-zzzzzzzzzzzzzzzz",
                "0123456789abcdef", "", null}) {
            ApiException failure = catchThrowableOfType(() -> Ids.requireRoomId(bad), ApiException.class);
            assertThat(failure).as("должен быть отклонён: %s", bad).isNotNull();
            assertThat(failure.getCode()).isEqualTo("ROOM_INVALID");
            assertThat(failure.getStatus()).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("Комната-лобби команды всегда одна и та же и выглядит как обычная комната")
    void teamLobbyRoomIdIsDeterministic() {
        String first = Ids.teamLobbyRoomId("team-red");

        assertThat(first).isEqualTo("hat-1400fc939dbc297f");
        assertThat(first).matches(Ids.ROOM.pattern());
        assertThat(Ids.teamLobbyRoomId("team-red")).isEqualTo(first);
        assertThat(Ids.teamLobbyRoomId("team-blue")).isNotEqualTo(first);
    }

    @Test
    @DisplayName("Ключ пары не зависит от порядка собеседников")
    void chatPairIsSymmetric() {
        assertThat(Ids.pair("uid-b", "uid-a")).isEqualTo(Ids.pair("uid-a", "uid-b"));
        assertThat(Ids.pair("uid-a", "uid-b")).isEqualTo("uid-a_uid-b");
    }

    @Test
    @DisplayName("Ключ поиска нормализуется, а из ничего получается пустая строка")
    void searchKeyIsNormalized() {
        assertThat(Ids.key("  Аня  ")).isEqualTo("аня");
        assertThat(Ids.key(null)).isEmpty();
    }

    @Test
    @DisplayName("Случайное число не выходит за границу, а нулевая граница не роняет расчёт")
    void randomIntStaysInRange() {
        for (int i = 0; i < 500; i++) {
            assertThat(Ids.randomInt(5)).isBetween(0, 4);
            assertThat(Ids.randomInt(10, 13)).isBetween(10, 12);
        }
        assertThat(Ids.randomInt(0)).isZero();
        assertThat(Ids.randomInt(7, 7)).isEqualTo(7);
    }

    @Test
    @DisplayName("Ник начинается с буквы и живёт в границах от трёх до двадцати знаков")
    void nicknameBounds() {
        assertThat(Ids.NICKNAME.matcher("Ann").matches()).isTrue();
        assertThat(Ids.NICKNAME.matcher("A_1").matches()).isTrue();
        assertThat(Ids.NICKNAME.matcher("A".repeat(20)).matches()).isTrue();
        assertThat(Ids.NICKNAME.matcher("A".repeat(21)).matches()).isFalse();
        assertThat(Ids.NICKNAME.matcher("An").matches()).isFalse();
        assertThat(Ids.NICKNAME.matcher("1Ann").matches()).isFalse();
        assertThat(Ids.NICKNAME.matcher("Аня").matches()).isFalse();
    }

    @Test
    @DisplayName("Название команды длиннее ника и допускает пробел и дефис, но не в начале")
    void teamNameBounds() {
        assertThat(Ids.TEAM_NAME.matcher("Red Hats").matches()).isTrue();
        assertThat(Ids.TEAM_NAME.matcher("1st-team").matches()).isTrue();
        assertThat(Ids.TEAM_NAME.matcher("A".repeat(30)).matches()).isTrue();
        assertThat(Ids.TEAM_NAME.matcher("A".repeat(31)).matches()).isFalse();
        assertThat(Ids.TEAM_NAME.matcher(" Red").matches()).isFalse();
        assertThat(Ids.TEAM_NAME.matcher("-Red").matches()).isFalse();
    }

    @Test
    @DisplayName("Идентификатор мема бывает только своим или встроенным")
    void memeIdBounds() {
        assertThat(Ids.MEME_ID.matcher("meme-abc123").matches()).isTrue();
        assertThat(Ids.MEME_ID.matcher("builtin-bmw-drugoy-ne-znayu").matches()).isTrue();
        assertThat(Ids.MEME_ID.matcher("meme-abc12").matches()).isFalse();
        assertThat(Ids.MEME_ID.matcher("other-abc123").matches()).isFalse();
    }

    @Test
    @DisplayName("Тест-бот узнаётся по приставке и строчным знакам")
    void testBotBounds() {
        assertThat(Ids.TEST_BOT.matcher("testbot-alpha-1").matches()).isTrue();
        assertThat(Ids.TEST_BOT.matcher("testbot-AL").matches()).isFalse();
        assertThat(Ids.TEST_BOT.matcher("testbot-ab").matches()).isFalse();
    }
}
