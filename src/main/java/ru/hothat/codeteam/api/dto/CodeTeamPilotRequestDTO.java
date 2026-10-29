package ru.hothat.codeteam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Ручной пилот: вводятся только согласованные тестовые события двух игроков. */
@Schema(description = "Запрос на анализ подтверждённых событий реальной команды из двух игроков")
public record CodeTeamPilotRequestDTO(
        @Schema(description = "Класс команды: пара, друзья или корпоративная") @NotBlank @Pattern(regexp = "pair|friends|corporate") String teamClass,
        @Schema(description = "Два игрока, участвовавших в матче и давших согласие") @NotNull @Size(min = 2, max = 2) List<@Valid Player> players,
        @Schema(description = "Проверенные события матча для краткого AI-анализа") @NotNull @Size(min = 2, max = 30) List<@Valid Evidence> evidence,
        @Schema(description = "True — реальный платный запрос GenAPI, false — бесплатный просмотр промпта") boolean live) {

    @Schema(description = "Один из двух участников анализируемой команды")
    public record Player(
            @Schema(description = "Идентификатор участника матча") @NotBlank @Size(max = 48) String id,
            @Schema(description = "Отображаемое имя участника") @NotBlank @Size(max = 80) String nickname,
            @Schema(description = "Согласие участника на анализ его игровых данных") boolean consented) {
    }

    @Schema(description = "Одно проверенное игровое событие, не выдуманное моделью")
    public record Evidence(
            @Schema(description = "Уникальный ID исходного события") @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,24}") String id,
            @Schema(description = "ID игрока, к которому относится событие") @NotBlank @Size(max = 48) String playerId,
            @Schema(description = "Тип зафиксированного события") @NotBlank @Pattern(regexp = "speech|guess|hint|turn|score") String type,
            @Schema(description = "Краткое описание проверенного события") @NotBlank @Size(max = 200) String text,
            @Schema(description = "Источник игровых данных") String source,
            @Schema(description = "Признак синтетической речи или события, исключаемого из реальных свидетельств") boolean synthetic) {
    }
}
