package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Принятое приглашение: место в комнате выдано тем же вызовом.
 *
 * <p>Принять приглашение и войти — одно действие, а не два. Раньше их было
 * два: старый {@code accept_room_invite} заводил место, а браузер следом
 * читал комнату и решал, пускать ли себя. Между ними умещался старт партии, и
 * человек оказывался с местом в комнате, куда его уже не впустят.
 */
@Schema(description = "Принятое приглашение в комнату")
public record AcceptedRoomInviteResponseDTO(

        @Schema(description = "Приглашение после принятия")
        RoomInviteStatusView invite,

        @Schema(description = "Паспорт комнаты")
        RoomSummaryView room,

        @Schema(description = "Место, которое получил приглашённый")
        RoomSeatView seat) {
}
