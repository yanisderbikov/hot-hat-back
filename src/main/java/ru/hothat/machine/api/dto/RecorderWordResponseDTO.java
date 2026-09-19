package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Слово текущего хода.
 *
 * <p>Заменяет режим {@code GET /api/recording-state?word=1}. Отдельный адрес
 * нужен потому, что слово меняется чаще всего остального, а зеркало комнаты
 * ради него тащить незачем.
 */
@Schema(description = "Слово текущего хода для записи")
public record RecorderWordResponseDTO(

        @Schema(description = "Совпала ли снимаемая партия с текущей партией комнаты")
        RecorderSceneState state,

        @Schema(description = "Комната съёмки", example = "hat-3f1c9a2b7d4e6501")
        String roomId,

        @Schema(description = "Номер снимаемой партии", example = "3", type = "integer")
        int gameNumber,

        @Schema(description = "Фаза комнаты", example = "active",
                allowableValues = {"setup", "turnIntro", "active", "appeal", "between", "finished", "closed"})
        String phase,

        @Schema(description = "Идентификатор текущего хода", example = "turn-6b1f0c", nullable = true)
        String turnId,

        @Schema(description = "Слово, которое объясняют прямо сейчас", example = "телескоп", nullable = true)
        String currentWord,

        @Schema(description = "Сколько слов осталось в шляпе; null при state=STALE_GAME",
                example = "17", type = "integer", nullable = true)
        Integer wordsLeft,

        @Schema(description = "Когда произошло последнее действие, мс эпохи; null при state=STALE_GAME",
                example = "1757068830000", type = "integer", format = "int64", nullable = true)
        Long lastActionAtMs,

        @Schema(description = "Серверное время, мс эпохи",
                example = "1757068800000", type = "integer", format = "int64")
        long serverNowMs) {
}
