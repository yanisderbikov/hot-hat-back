package ru.hothat.lobby.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Игрок так, как его видно из лобби.
 *
 * <p>Общая проекция состава: включается полем в превью комнаты, а не
 * копируется в каждый ответ.
 *
 * <p>Из документа игрока сюда попадает только видимое на экране. Ни обоймы
 * мемов, ни очереди повторного розыгрыша, ни срока кулдауна диверсий — это
 * данные хода, а не витрины, и в браузер незнакомого зрителя им не место.
 */
@Schema(description = "Игрок в составе комнаты")
public record LobbyPlayerView(

        @Schema(description = "Идентификатор игрока", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String uid,

        @Schema(description = "Имя, под которым игрок сидит в этой комнате", example = "vasya")
        String nickname,

        @Schema(description = "Аватар как data-URL; null — аватара нет",
                example = "data:image/webp;base64,UklGRh4AAABXRUJQ", nullable = true)
        String avatarDataUrl,

        @Schema(description = "Команда игрока; null — игрок ещё не сел за стол",
                example = "team-1", nullable = true)
        String teamId,

        @Schema(description = "Тестовый ли это бот: у бота не показывают голосовые эффекты",
                example = "false", type = "boolean")
        boolean testBot,

        @Schema(description = "Боезапас игрока")
        LobbyArsenalView arsenal) {
}
