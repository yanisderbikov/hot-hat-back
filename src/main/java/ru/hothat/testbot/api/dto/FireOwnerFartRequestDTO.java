package ru.hothat.testbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/** Ручная диверсия владельца в тестовой комнате. */
@Schema(description = "Запрос на «пук» владельца")
public record FireOwnerFartRequestDTO(

        /**
         * Свой идентификатор события, чтобы клиент не проиграл один и тот же
         * звук дважды. Формат тот же, что проверял старый движок строкой;
         * пустое значение допустимо — сервер выдаст свой.
         */
        @Schema(description = "Идентификатор события для защиты от повтора; можно не задавать",
                example = "fart-a1b2c3d4e5", pattern = "^fart-[A-Za-z0-9_-]{8,220}$", nullable = true)
        @Pattern(regexp = "^fart-[A-Za-z0-9_-]{8,220}$", message = "Некорректный идентификатор события.")
        String eventId) {
}
