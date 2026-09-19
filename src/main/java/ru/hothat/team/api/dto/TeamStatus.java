package ru.hothat.team.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Состояние рейтинговой команды.
 *
 * <p>{@code pending} — основатель позвал напарника, но тот ещё не ответил:
 * команда существует, играть ею нельзя. {@code active} — приглашение принято.
 * Третьего состояния нет: отказ напарника удаляет команду вместе с её именем.
 */
@Schema(description = "Состояние команды")
public enum TeamStatus {

    PENDING("pending"),
    ACTIVE("active");

    private final String wireValue;

    TeamStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /** Значение из базы; неизвестное считаем неподтверждённой командой. */
    public static TeamStatus fromWire(String value) {
        return ACTIVE.wireValue.equals(value) ? ACTIVE : PENDING;
    }
}
