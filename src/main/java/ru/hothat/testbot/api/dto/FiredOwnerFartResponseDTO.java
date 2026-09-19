package ru.hothat.testbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Выпущенная диверсия владельца. */
@Schema(description = "Событие, разосланное участникам комнаты")
public record FiredOwnerFartResponseDTO(

        @Schema(description = "Созданное событие диверсии")
        SabotageEventView event) {
}
