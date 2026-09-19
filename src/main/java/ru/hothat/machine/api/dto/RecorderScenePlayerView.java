package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Игрок в кадре.
 *
 * <p>Аватар остаётся картинкой в data-URL, а не ссылкой: файлового хранилища
 * аватаров в проекте нет, все области отдают {@code avatarDataUrl}, и страница
 * записи рисует плитку именно из него ({@code recording-view.js:95}).
 */
@Schema(description = "Игрок партии глазами рекордера")
public record RecorderScenePlayerView(

        @Schema(description = "Идентификатор игрока", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String uid,

        @Schema(description = "Имя игрока в комнате", example = "Вася")
        String name,

        @Schema(description = "Команда игрока; пусто, если он ещё не в составе", example = "team-1")
        String teamId,

        @Schema(description = "Тестовый бот, а не человек", example = "false", type = "boolean")
        boolean testBot,

        @Schema(description = "Аватар картинкой в data-URL; пусто, если аватара нет",
                example = "data:image/webp;base64,UklGRh4AAABXRUJQ", nullable = true)
        String avatarDataUrl,

        @Schema(description = "Камера включена", example = "true", type = "boolean")
        boolean cameraEnabled,

        @Schema(description = "Микрофон включён", example = "true", type = "boolean")
        boolean microphoneEnabled) {
}
