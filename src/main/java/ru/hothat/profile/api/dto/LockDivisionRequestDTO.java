package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Закрепить дивизион за учётной записью. */
@Schema(description = "Запрос на закрепление дивизиона")
public record LockDivisionRequestDTO(

        /**
         * Выбор однократный: повторный вызов с другим кодом отвечает 409
         * {@code DIVISION_LOCKED}. Иначе рейтинги разных языков смешались бы.
         */
        @Schema(description = "Дивизион, за которым игрок закрепляется навсегда", example = "ru")
        @NotNull(message = "Дивизион обязателен.")
        DivisionLanguage divisionLanguage) {
}
