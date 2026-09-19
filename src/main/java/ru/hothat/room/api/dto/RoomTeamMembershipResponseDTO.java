package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Игрок сел в команду.
 *
 * <p>В ответе обе команды: та, куда сел, и та, откуда вышел. Пересадка — это
 * всегда две записи, и до сих пор их делала клиентская транзакция, читая
 * составы обеих команд у себя ({@code joinTeam()},
 * {@code app-core.js:12576}). Отдавать только новую значило бы заставить экран
 * догадываться, что старая опустела.
 */
@Schema(description = "Членство игрока в команде")
public record RoomTeamMembershipResponseDTO(

        @Schema(description = "Команда, в которую сел игрок")
        RoomTeamView team,

        @Schema(description = "Команда, из которой он вышел; null — он не состоял ни в какой",
                nullable = true)
        RoomTeamView previousTeam,

        @Schema(description = "Его место после пересадки")
        RoomSeatView seat) {
}
