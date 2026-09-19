package ru.hothat.testbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Отряд ботов, поднятый в тестовой комнате. */
@Schema(description = "Состав тестовой комнаты после подъёма ботов")
public record TestBotSquadResponseDTO(

        @Schema(description = "Комната, в которой поднят отряд",
                example = "hat-0f3a9c1d7b2e5480", pattern = "^hat-[a-f0-9]{16}$")
        String roomId,

        @Schema(description = "Идентификаторы созданных ботов в порядке рассадки",
                example = "[\"testbot-alpha\", \"testbot-bravo\"]")
        List<String> botIds,

        @Schema(description = "Порядок команд: по этому списку боты разведены по сторонам",
                example = "[\"team-a\", \"team-b\"]")
        List<String> teamOrder,

        @Schema(description = "Предел участников комнаты, выставленный под размер отряда", example = "10", type = "integer")
        int maxPlayers) {
}
