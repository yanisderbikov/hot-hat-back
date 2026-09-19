package ru.hothat.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Смена своего пароля тем, кто знает текущий.
 *
 * <p>Новое поле названо {@code newPassword}, а не {@code password}: в старом
 * запросе рядом стояли {@code currentPassword} и {@code password}, и какое из
 * двух новое — приходилось угадывать по имени соседа.
 */
@Schema(description = "Смена собственного пароля")
public record ChangeOwnPasswordRequestDTO(

        @Schema(description = "Текущий пароль: им подтверждается, что за клавиатурой владелец",
                example = "s3cret-pass", maxLength = 128, format = "password")
        @NotBlank(message = "Не указан текущий пароль.")
        @Size(max = 128, message = "Слишком длинный пароль.")
        String currentPassword,

        @Schema(description = "Новый пароль, не короче 6 знаков", example = "n3w-s3cret",
                minLength = 6, maxLength = 128, format = "password")
        @NotBlank(message = "Не указан новый пароль.")
        @Size(min = 6, max = 128, message = "Пароль: от 6 до 128 знаков.")
        String newPassword) {
}
