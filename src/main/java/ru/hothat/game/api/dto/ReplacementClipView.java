package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Клип Подмены: снятые заранее десять секунд объясняющего. */
@Schema(description = "Клип Подмены")
public record ReplacementClipView(

        @Schema(description = "Идентификатор клипа", example = "repl_9f8e7d6c5b4a39281706")
        String clipId,

        @Schema(description = "Кто снимал", example = "pL9Mn2bV3cX4zA5sD6fG7hJ8kL9m")
        String attackerUid,

        @Schema(description = "Кого сняли", example = "kZ8Qw1nBv2mX3cL4aS5dF6gH7jK8")
        String targetUid,

        @Schema(description = "Ход, в котором снят клип: в нём же применить его нельзя",
                example = "turn_3f9a1c04b77e2d15")
        String recordedTurnId,

        @Schema(description = "Номер партии", example = "3")
        int gameNumber,

        @Schema(description = "Когда началась съёмка, миллисекунды эпохи", example = "1757150380000")
        long createdAtMs,

        @Schema(description = "Съёмка закончилась удачно и клип готов к применению", example = "true")
        boolean ready) {
}
