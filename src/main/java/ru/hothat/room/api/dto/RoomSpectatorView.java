package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Зритель комнаты.
 *
 * <p>Лёгкие подписки превью с главной сюда не попадают: они помечены
 * {@code preview} и зрителями комнаты не считаются — иначе счётчик над столом
 * показывал бы всех, кто пролистал витрину.
 */
@Schema(description = "Зритель комнаты")
public record RoomSpectatorView(

        @Schema(description = "Идентификатор зрителя", example = "2c9d4e7aP3lM5nB8vC1xZ0qW6eR4")
        String uid,

        @Schema(description = "Имя зрителя", example = "petya")
        String name,

        @Schema(description = "Аватар как data-URL; null — аватара нет",
                example = "data:image/webp;base64,UklGRh4AAABXRUJQ", nullable = true)
        String avatarDataUrl,

        @Schema(description = "Когда зрителя видели в последний раз, миллисекунды эпохи",
                example = "1788600000000", type = "integer")
        long lastSeenAtMs) {
}
