package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Название комнаты после правки.
 *
 * <p>Отдаётся то, что записано, а не то, что прислали: сервер обрезает длину
 * и схлопывает пробелы, и экран должен показать итог, а не свой черновик.
 */
@Schema(description = "Название комнаты")
public record RoomNameResponseDTO(

        @Schema(description = "Комната", example = "hat-0f3a9c1d7b2e5480")
        String roomId,

        @Schema(description = "Записанное название", example = "Пятничная шляпа")
        String name) {
}
