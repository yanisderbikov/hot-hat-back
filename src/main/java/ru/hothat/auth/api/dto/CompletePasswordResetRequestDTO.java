package ru.hothat.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Завершение восстановления: новый пароль по одноразовой ссылке.
 *
 * <p>Токен приходит из письма, а не из заголовка: у того, кто забыл пароль,
 * сессии нет. Он одноразовый, живёт час и хранится в базе хешем.
 */
@Schema(description = "Новый пароль по ссылке восстановления")
public record CompletePasswordResetRequestDTO(

        @Schema(description = "Токен из ссылки восстановления",
                example = "9wUvTn2xR7mL0pWzYcBdFgHjKsNvUaXe1rTyUiOpAsD", maxLength = 512)
        @NotBlank(message = "Не указан токен восстановления.")
        @Size(max = 512, message = "Некорректный токен восстановления.")
        String token,

        @Schema(description = "Новый пароль, не короче 6 знаков", example = "n3w-s3cret",
                minLength = 6, maxLength = 128, format = "password")
        @NotBlank(message = "Не указан новый пароль.")
        @Size(min = 6, max = 128, message = "Пароль: от 6 до 128 знаков.")
        String newPassword) {
}
