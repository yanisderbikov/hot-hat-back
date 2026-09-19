package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Новое название комнаты. Повторные пробелы сервер схлопывает. */
@Schema(description = "Новое название комнаты")
public record RenameRoomRequestDTO(

        @Schema(description = "Название комнаты", example = "Пятничная шляпа", maxLength = 80)
        @NotBlank(message = "У комнаты должно быть название.")
        @Size(max = 80, message = "Название комнаты не длиннее 80 символов.")
        String name) {
}
