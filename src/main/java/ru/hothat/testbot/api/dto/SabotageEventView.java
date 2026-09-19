package ru.hothat.testbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Событие диверсии так, как его получают участники комнаты.
 *
 * <p>Общая проекция: включается в ответы разных операций как поле, а не
 * копируется в каждую. Поля мема заполнены только у диверсии типа {@code meme}.
 */
@Schema(description = "Событие диверсии")
public record SabotageEventView(

        @Schema(description = "Идентификатор события", example = "fart-a1b2c3d4e5")
        String id,

        @Schema(description = "Вид диверсии", example = "fart")
        String type,

        @Schema(description = "Кто выпустил", example = "u-8f3a2b1c")
        String attackerUid,

        @Schema(description = "Как показать имя выпустившего", example = "Ведущий")
        String attackerName,

        @Schema(description = "По кому: объясняющий на момент выстрела", example = "u-2c9d4e7a", nullable = true)
        String targetUid,

        @Schema(description = "Момент создания, миллисекунды эпохи", example = "1788600000000", type = "integer")
        long createdAtMs,

        @Schema(description = "Сколько длится эффект, миллисекунды; 0 — мгновенное", example = "0", type = "integer")
        long durationMs,

        @Schema(description = "Номер партии, к которой относится событие", example = "3", type = "integer")
        int gameNumber) {
}
