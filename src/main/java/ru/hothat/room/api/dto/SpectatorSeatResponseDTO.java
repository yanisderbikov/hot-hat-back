package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Место зрителя в комнате.
 *
 * <p>Зритель не занимает места за столом и не влияет на вместимость, поэтому
 * ответ не называет ни команд, ни составов: смотреть можно и не зная, кто в
 * какой команде, — это придёт снимком комнаты.
 */
@Schema(description = "Место зрителя в комнате")
public record SpectatorSeatResponseDTO(

        @Schema(description = "Паспорт комнаты")
        RoomSummaryView room,

        @Schema(description = "Место зрителя")
        RoomSpectatorView spectator,

        @Schema(description = "Место заведено этим вызовом; false — зритель уже смотрел эту комнату",
                example = "true", type = "boolean")
        boolean created) {
}
