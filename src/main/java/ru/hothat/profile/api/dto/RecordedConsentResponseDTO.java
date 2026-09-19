package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Зарегистрированное согласие.
 *
 * <p>Отдаётся то, что действительно записано: момент проставляет сервер,
 * и без него клиент не смог бы показать «принято такого-то числа», не
 * догадываясь о часах сервера по своим.
 */
@Schema(description = "Согласие, записанное в учётную запись")
public record RecordedConsentResponseDTO(

        @Schema(description = "Момент принятия, миллисекунды эпохи", example = "1788600000000", type = "integer")
        long acceptedAtMs,

        @Schema(description = "Подтверждено ли совершеннолетие", example = "true", type = "boolean")
        boolean adultConfirmed,

        @Schema(description = "Версии, сохранённые в учётной записи")
        ConsentVersionsView versions) {
}
