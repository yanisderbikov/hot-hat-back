package ru.hothat.room.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Что стало с приглашением в комнату.
 *
 * <p>Сегодня это свободная строка, которую собирает
 * {@code SocialServiceImpl.roomInviteAvailability}: девять значений, ни одно
 * из них нигде не объявлено, и клиент сравнивает их литералами. Набор закрыт
 * здесь, потому что от значения зависит и надпись на карточке в переписке, и
 * то, есть ли на ней кнопка.
 *
 * <p>Три значения переименованы из {@code room_missing}, {@code room_closed} и
 * {@code game_started} в общий для новой поверхности camelCase. Это
 * сознательная правка контракта: старый адрес продолжает отвечать
 * по-старому, а фронтенд переедет на новый вместе с остальными приглашениями.
 */
@Schema(description = "Состояние приглашения в комнату")
public enum RoomInviteState {

    /** Ждёт ответа и войти по нему можно. */
    PENDING("pending"),
    /** Уже принято; повторный вход по нему тоже разрешён. */
    ACCEPTED("accepted"),
    /** Срок жизни вышел: приглашения живут сутки. */
    EXPIRED("expired"),
    /** Такого приглашения нет вовсе. */
    MISSING("missing"),
    /** Приглашение чужое: спрашивающий не отправитель и не получатель. */
    FORBIDDEN("forbidden"),
    /** Комнаты больше нет: её подмели или закрыл хозяин. */
    ROOM_MISSING("roomMissing"),
    /** Комната закрыта. */
    ROOM_CLOSED("roomClosed"),
    /** Партия уже началась или сыграна: поколение партии разошлось с записанным. */
    GAME_STARTED("gameStarted"),
    /** Отозвано или отклонено: войти нельзя, причина не важна получателю. */
    UNAVAILABLE("unavailable");

    private final String wireValue;

    RoomInviteState(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /** Можно ли по этому приглашению войти в комнату. */
    public boolean available() {
        return this == PENDING || this == ACCEPTED;
    }
}
