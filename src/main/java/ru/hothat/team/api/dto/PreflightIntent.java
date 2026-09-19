package ru.hothat.team.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Зачем пара проходит проверку готовности.
 *
 * <p>{@code quick} — команда встанет в очередь подбора и ждёт, кого дадут;
 * {@code room} — команда целится в конкретную открытую рейтинговую комнату,
 * и тогда обязателен её идентификатор.
 *
 * <p>Старый движок читал это поле как {@code "room".equals(...) ? "room" :
 * "quick"}: любая опечатка тихо превращалась в быструю игру, и пара,
 * собравшаяся к друзьям в комнату, уезжала к случайным соперникам.
 */
@Schema(description = "Замысел проверки готовности")
public enum PreflightIntent {

    QUICK("quick"),
    ROOM("room");

    private final String wireValue;

    PreflightIntent(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /** Значение из базы; неизвестное считаем быстрой игрой, как и движок. */
    public static PreflightIntent fromWire(String value) {
        return ROOM.wireValue.equals(value) ? ROOM : QUICK;
    }
}
