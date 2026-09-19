package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Комната, вернувшаяся к набору.
 *
 * <p>Сводит две клиентские процедуры в одну. {@code backToSetup()}
 * ({@code app-core.js:13508}) обнулял счёт и стирал состояние партии, оставляя
 * команды; {@code resetRoom()} ({@code :13570}) вдобавок удалял команды и
 * сданные слова, причём тремя отдельными записями с промежуточной фазой
 * {@code resetting} — оборвись вкладка посередине, комната зависала в ней
 * навсегда. Здесь это один сценарий и одна транзакция, а что именно стёрли,
 * видно по полю {@code teamsCleared}.
 */
@Schema(description = "Комната после возврата к набору")
public record RoomSetupResponseDTO(

        @Schema(description = "Паспорт комнаты")
        RoomSummaryView room,

        @Schema(description = "Команды после возврата: пусто, если их распустили")
        List<RoomTeamView> teams,

        @Schema(description = "Команды и сданные слова стёрты", example = "false", type = "boolean")
        boolean teamsCleared,

        @Schema(description = "Сколько сданных наборов слов удалили", example = "0", type = "integer")
        int wordSubmissionsCleared) {
}
