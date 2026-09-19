package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Итог снятия паузы хозяином.
 *
 * <p>Снятие не всегда возобновляет партию: пока кто-то не вернулся на связь,
 * пауза остаётся, только меняет причину. Одна форма ответа на оба исхода —
 * поле {@code resumed} и список тех, кого ещё ждут.
 */
@Schema(description = "Итог снятия паузы")
public record MatchResumedResponseDTO(

        @Schema(description = "Партия пошла дальше", example = "true")
        boolean resumed,

        @Schema(description = "Состояние паузы после нажатия")
        PauseView pause,

        @Schema(description = "Кого ещё ждут на связи", example = "[]")
        List<String> stillMissingUids,

        @Schema(description = "Ход продолжается до этого момента, миллисекунды эпохи; ноль вне хода",
                example = "1757150408400")
        long turnDeadlineMs) {
}
