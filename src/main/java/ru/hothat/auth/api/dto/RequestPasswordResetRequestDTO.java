package ru.hothat.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Заявка на восстановление пароля по адресу почты. */
@Schema(description = "Заявка на восстановление пароля")
public record RequestPasswordResetRequestDTO(

        @Schema(description = "Почта аккаунта", example = "player@example.com", maxLength = 320)
        @NotBlank(message = "Не указан e-mail.")
        @Email(message = "Некорректный e-mail.")
        @Size(max = 320, message = "Слишком длинный e-mail.")
        String email) {
}
