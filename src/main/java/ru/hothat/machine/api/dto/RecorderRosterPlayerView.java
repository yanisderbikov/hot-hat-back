package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Игрок в составе, каким его получает рекордер при снаряжении.
 *
 * <p>Отличается от {@link RecorderScenePlayerView} не случайно: снаряжение
 * происходит один раз, поэтому здесь есть арсенал и время последнего
 * появления, а сцену рекордер опрашивает четыре раза в секунду
 * ({@code recording-view.js:37}) — там этих полей нет, чтобы не гонять их
 * впустую.
 */
@Schema(description = "Игрок в составе комнаты при снаряжении рекордера")
public record RecorderRosterPlayerView(

        @Schema(description = "Идентификатор игрока", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String uid,

        @Schema(description = "Имя игрока в комнате", example = "Вася")
        String name,

        @Schema(description = "Команда игрока; пусто, если он ещё не в составе", example = "team-1")
        String teamId,

        @Schema(description = "Тестовый бот, а не человек", example = "false", type = "boolean")
        boolean testBot,

        @Schema(description = "Когда игрока видели в последний раз, мс эпохи; 0 — ни разу",
                example = "1757068800000", type = "integer", format = "int64")
        long lastSeenAtMs,

        @Schema(description = "Камера включена", example = "true", type = "boolean")
        boolean cameraEnabled,

        @Schema(description = "Микрофон включён", example = "true", type = "boolean")
        boolean microphoneEnabled,

        @Schema(description = "Боезапас по видам диверсий: ключ — вид, значение — сколько осталось",
                example = "{\"tomato\":7,\"meme\":4}")
        Map<String, Integer> arsenal) {
}
