package ru.hothat.chat.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Чем является сообщение переписки.
 *
 * <p>Сегодня этого понятия на сервере нет: поле {@code type} заполняется
 * только у приглашения в комнату, а «текст это или фото» интерфейс
 * восстанавливает сам, заглядывая в {@code attachment.kind}
 * ({@code realtime-social.js:97}, {@code friends.js:14}). Три места
 * повторяют одно и то же ветвление, и каждое может ошибиться по-своему.
 * Здесь вид называет сервер, а набор значений закрыт.
 */
@Schema(description = "Вид сообщения личной переписки")
public enum ChatMessageKind {

    /** Обычный текст без вложения. */
    TEXT("text"),
    /** Фотография: заполнено поле {@code image}. */
    IMAGE("image"),
    /** Поделённая запись игры: заполнено поле {@code recording}. */
    RECORDING("recording"),
    /** Приглашение в комнату: заполнено поле {@code roomInvite}. */
    ROOM_INVITE("room_invite");

    private final String wireValue;

    ChatMessageKind(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
