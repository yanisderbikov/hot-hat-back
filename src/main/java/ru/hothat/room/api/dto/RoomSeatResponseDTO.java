package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Место игрока в комнате после входа.
 *
 * <p>Одна форма ответа на все три случая, которые фронтенд до сих пор различал
 * сам: первый вход, возвращение по перезагрузке и возвращение в идущую партию.
 * Что именно произошло, видно по полям — {@code created} и
 * {@code restoredToMatch}, — а не по тому, каких ключей в ответе не оказалось.
 *
 * <p>Паспорт комнаты едет вместе с местом, потому что клиенту он нужен
 * немедленно: по фазе он решает, какой экран показать, а вторым запросом
 * это была бы вторая поездка до того, как что-то нарисовано.
 */
@Schema(description = "Место игрока в комнате")
public record RoomSeatResponseDTO(

        @Schema(description = "Паспорт комнаты")
        RoomSummaryView room,

        @Schema(description = "Место вошедшего")
        RoomSeatView seat,

        @Schema(description = "Место заведено этим вызовом; false — игрок вернулся на своё",
                example = "true", type = "boolean")
        boolean created,

        @Schema(description = "Игрок вернулся в идущую партию по замороженному составу",
                example = "false", type = "boolean")
        boolean restoredToMatch) {
}
