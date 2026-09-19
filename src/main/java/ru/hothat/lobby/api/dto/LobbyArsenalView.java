package ru.hothat.lobby.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Боезапас игрока так, как его показывает превью.
 *
 * <p>Эти четыре счётчика и сегодня видны любому зрителю: плитка превью рисует
 * их подписью под именем ({@code live-preview.js:60}). Секрета в них нет —
 * секрет в комнате один, текущее слово, и его здесь нет.
 */
@Schema(description = "Боезапас игрока")
public record LobbyArsenalView(

        @Schema(description = "Сколько мем-роликов осталось", example = "3", type = "integer")
        int meme,

        @Schema(description = "Сколько помидоров осталось", example = "2", type = "integer")
        int tomato,

        @Schema(description = "Сколько крокодилов осталось", example = "1", type = "integer")
        int crocodile,

        @Schema(description = "Сколько голосовых эффектов осталось", example = "4", type = "integer")
        int voice) {
}
