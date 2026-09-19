package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Учётная запись в реестре владельца. */
@Schema(description = "Учётная запись в реестре")
public record AdminUserCardView(

        @Schema(description = "Игрок", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String uid,

        @Schema(description = "Ник; если ника нет — построенный из почты", example = "vasya")
        String nickname,

        @Schema(description = "Почта; null — почта не сохранена", example = "player@example.com",
                nullable = true)
        String email,

        @Schema(description = "Аватар как data-URL; null — аватара нет",
                example = "data:image/webp;base64,UklGRl…", nullable = true)
        String avatarDataUrl,

        @Schema(description = "Языковой дивизион", example = "ru",
                allowableValues = {"ru", "en", "de", "es", "fr", "it", "zh", "ja", "kk"})
        String divisionLanguage,

        @Schema(description = "Когда зарегистрировался; null — время не сохранилось",
                example = "1788596400000", type = "integer", nullable = true)
        Long registeredAtMs,

        @Schema(description = "Учётка заблокирована", example = "false", type = "boolean")
        boolean banned) {
}
