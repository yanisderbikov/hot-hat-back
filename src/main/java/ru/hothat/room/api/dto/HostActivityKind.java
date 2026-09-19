package ru.hothat.room.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Чем именно хозяин комнаты подтвердил, что он ещё здесь.
 *
 * <p>Сегодня это свободная строка: {@code setup_host_activity} принимает
 * {@code kind} и кладёт в колонку первые сорок символов чего угодно
 * ({@code GameServiceImpl:346}). Отправляет её фронтенд из десяти мест —
 * от движения указателя до правки названия комнаты, — и ни одно из значений
 * нигде не описано. Набор закрыт здесь, потому что значение видно человеку:
 * подсказка «хозяин печатал слова» и «хозяин водил мышью» — разные сообщения,
 * и различать их по нормализованной строке нельзя.
 */
@Schema(description = "Чем хозяин подтвердил присутствие")
public enum HostActivityKind {

    /** Нажатие или касание. */
    POINTER("pointer"),
    /** Движение указателя. */
    POINTER_MOVE("pointer_move"),
    /** Нажатие клавиши. */
    KEYBOARD("keyboard"),
    /** Ввод в поле. */
    TYPING("typing"),
    /** Прокрутка экрана. */
    SCROLL("scroll"),
    /** Работа с составами команд. */
    TEAM("team"),
    /** Сдача слов в шляпу. */
    WORDS("words"),
    /** Правка мем-обоймы. */
    MEME_LOADOUT("meme_loadout"),
    /** Правка названия и расположения комнаты. */
    ROOM_IDENTITY("room_identity"),
    /** Всё прочее: клиент не назвал причину. */
    ACTIVITY("activity");

    private final String wireValue;

    HostActivityKind(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
