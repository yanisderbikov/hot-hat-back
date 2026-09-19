package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Игрок в комнате так, как его рисует экран.
 *
 * <p>Общая проекция: включается полем и в снимок комнаты, и в ответы на вход,
 * на отметку присутствия и на перевод зрителя в игроки, поэтому форма места
 * описана один раз.
 *
 * <p>Из строки игрока сюда не попадают его боезапас, обойма мемов, очередь
 * повторного розыгрыша и кулдаун диверсий: это данные партии, они уезжают по
 * своим адресам в {@code /api/v2/game} и по своим правам. До сих пор всё это
 * приезжало каждому в комнате живой подпиской на {@code rooms/{id}/players}.
 * Счётчики зарядов, которые плитка рисует у каждого, едут рядом с партией —
 * полем {@code ammo} снимка партии и кадра канала, — а не здесь: место
 * принадлежит комнате, а заряды — партии.
 */
@Schema(description = "Место игрока в комнате")
public record RoomSeatView(

        @Schema(description = "Идентификатор игрока", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String uid,

        @Schema(description = "Имя, под которым игрок сидит в этой комнате", example = "vasya")
        String name,

        @Schema(description = "Аватар как data-URL; null — аватара нет",
                example = "data:image/webp;base64,UklGRh4AAABXRUJQ", nullable = true)
        String avatarDataUrl,

        @Schema(description = "Команда игрока; null — игрок ещё не сел за стол",
                example = "team-1", nullable = true)
        String teamId,

        @Schema(description = "Тестовый ли это бот", example = "false", type = "boolean")
        boolean testBot,

        @Schema(description = "Считается ли игрок живым: окно живости — правило сервера, "
                + "а не браузера", example = "true", type = "boolean")
        boolean alive,

        @Schema(description = "Когда игрока видели в последний раз, миллисекунды эпохи",
                example = "1788600000000", type = "integer")
        long lastSeenAtMs,

        @Schema(description = "Включена ли у игрока камера", example = "true", type = "boolean")
        boolean cameraEnabled,

        @Schema(description = "Включён ли у игрока микрофон", example = "true", type = "boolean")
        boolean microphoneEnabled) {
}
