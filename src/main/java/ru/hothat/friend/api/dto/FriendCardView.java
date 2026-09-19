package ru.hothat.friend.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Друг так, как его рисует список друзей.
 *
 * <p>Общая проекция: включается полем в ответы разных операций, а не
 * копируется в каждую. Ник здесь уже разрешён сервером — профиль, индекс
 * ников, запасное «playerXXXXXX»; клиенту не нужно повторять эту лестницу.
 */
@Schema(description = "Карточка друга")
public record FriendCardView(

        @Schema(description = "Идентификатор игрока", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk",
                pattern = "^[A-Za-z0-9]{28}$")
        String uid,

        @Schema(description = "Разрешённый ник: профиль, индекс ников или запасное имя по хвосту uid",
                example = "vasya")
        String nickname,

        @Schema(description = "Аватар как data-URL; null — аватара нет",
                example = "data:image/webp;base64,UklGRh4AAABXRUJQ", nullable = true)
        String avatarDataUrl,

        @Schema(description = "Когда игрока видели в последний раз, миллисекунды эпохи; 0 — не видели ни разу",
                example = "1788600000000", type = "integer")
        long lastSeenAtMs,

        @Schema(description = "Дивизион игрока", example = "ru",
                allowableValues = {"ru", "en", "de", "es", "fr", "it", "zh", "ja", "kk"})
        String divisionLanguage) {
}
