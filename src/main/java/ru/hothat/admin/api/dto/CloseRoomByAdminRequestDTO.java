package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Почему администратор закрывает комнату. */
@Schema(description = "Запрос на закрытие комнаты администратором")
public record CloseRoomByAdminRequestDTO(

        @Schema(description = "Причина закрытия; не задана — «Закрыто администратором»",
                example = "Жалобы участников", nullable = true)
        @Size(max = 200, message = "Причина длиннее 200 символов не сохраняется.")
        String reason) {
}
