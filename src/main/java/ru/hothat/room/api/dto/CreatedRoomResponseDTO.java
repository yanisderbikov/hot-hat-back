package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Заведённая комната и место её хозяина в ней.
 *
 * <p>Оба в одном ответе, потому что оба создаются одной транзакцией: комната
 * без хозяина за столом — это комната, которую тут же подметёт уборщик. До сих
 * пор их писал один пакет в браузере, и между двумя записями было окно, в
 * котором комната существовала пустой.
 */
@Schema(description = "Созданная комната")
public record CreatedRoomResponseDTO(

        @Schema(description = "Паспорт новой комнаты")
        RoomSummaryView room,

        @Schema(description = "Место создателя: он же первый игрок и хозяин")
        RoomSeatView seat) {
}
