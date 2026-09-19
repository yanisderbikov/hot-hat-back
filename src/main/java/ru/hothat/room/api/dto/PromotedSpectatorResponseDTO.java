package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Зритель, которого хозяин посадил за стол.
 *
 * <p>Зрительское место при этом снимается: одно и то же лицо не может быть
 * одновременно в составе и в зале — иначе счётчик зрителей над столом считал
 * бы игроков.
 */
@Schema(description = "Зритель, переведённый в игроки")
public record PromotedSpectatorResponseDTO(

        @Schema(description = "Место, которое он занял")
        RoomSeatView seat,

        @Schema(description = "Сколько мемов у него заряжено: без пяти в партию не пускают",
                example = "5", type = "integer")
        int loadoutCount) {
}
