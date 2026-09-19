package ru.hothat.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Выход с текущего устройства: гасится предъявленный refresh-токен.
 *
 * <p>Тело, а не заголовок, по той же причине, что и у обновления, и с той же
 * формой — но запись своя: у выхода нет и не будет ответа, а у обновления он
 * есть, и общий DTO связал бы две операции с разными судьбами.
 */
@Schema(description = "Выход с текущего устройства")
public record RevokeSessionRequestDTO(

        @Schema(description = "Refresh-токен этой сессии",
                example = "8kQvTn2xR7mL0pWzYcBdFgHjKsNvUaXe1rTyUiOpAsD", maxLength = 512)
        @NotBlank(message = "Не указан refresh-токен.")
        @Size(max = 512, message = "Некорректный refresh-токен.")
        String refreshToken) {
}
