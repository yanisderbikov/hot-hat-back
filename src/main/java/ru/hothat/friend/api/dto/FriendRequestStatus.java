package ru.hothat.friend.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Судьба заявки в друзья.
 *
 * <p>Сегодня это свободная строка в колонке {@code friend_request.status};
 * набор значений нигде не объявлен и восстанавливается чтением
 * {@code SocialServiceImpl}. Здесь он закрыт: клиент перестаёт сравнивать
 * строки, а чужое значение отвергается до входа в контроллер.
 */
@Schema(description = "Состояние заявки в друзья")
public enum FriendRequestStatus {

    /** Ответа ещё нет. */
    PENDING("pending"),
    /** Заявку приняли — дружба существует. */
    ACCEPTED("accepted"),
    /** Заявку отклонили. */
    DECLINED("declined");

    private final String wireValue;

    FriendRequestStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /** Значение из старого движка; неизвестное считаем ожидающим ответа. */
    public static FriendRequestStatus fromWire(String value) {
        for (FriendRequestStatus status : values()) {
            if (status.wireValue.equals(value)) {
                return status;
            }
        }
        return PENDING;
    }
}
