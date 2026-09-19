package ru.hothat.app.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Что именно произошло у клиента.
 *
 * <p>Сегодня это свободная строка, которую сервер сверяет с набором из шести
 * значений уже внутри сценария ({@code AnalyticsServiceImpl.EVENT_TYPES}) и
 * отвечает на опечатку общим {@code ANALYTICS_EVENT_INVALID 400}. Здесь набор
 * закрыт перечислением: Jackson отвергает чужое значение до входа в
 * контроллер и называет в ошибке допустимые.
 *
 * <p>Расширять набор со стороны клиента нельзя намеренно: событие, которого
 * не ждёт отчёт, — это строка в таблице, которую никто никогда не прочтёт.
 */
@Schema(description = "Вид клиентского события")
public enum AnalyticsEventType {

    /** Игрок создал комнату. */
    ROOM_CREATED("room_created"),
    /** Игрок вошёл в чужую комнату. */
    ROOM_JOINED("room_joined"),
    /** В комнате началась партия. */
    GAME_STARTED("game_started"),
    /** Партия доиграна до конца. */
    GAME_FINISHED("game_finished"),
    /** Первая регистрация учётки. */
    ACCOUNT_REGISTERED("account_registered"),
    /** Вход в уже существующую учётку. */
    ACCOUNT_LOGIN("account_login");

    private final String wireValue;

    AnalyticsEventType(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
