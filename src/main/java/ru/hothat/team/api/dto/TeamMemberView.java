package ru.hothat.team.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Участник команды так, как его рисует карточка.
 *
 * <p>Общая проекция: включается полем в карточку команды, а не копируется в
 * каждый ответ.
 *
 * <p>Сводит воедино три параллельные структуры старого ответа —
 * {@code memberUids}, {@code memberNicknames} и карту {@code memberAvatars}.
 * Клиент связывал их по индексу и по ключу одновременно
 * ({@code memberNicknames?.[i]}, {@code memberAvatars?.[uid]}), и любое
 * расхождение длин показывало чужой аватар рядом с чужим именем.
 */
@Schema(description = "Участник команды")
public record TeamMemberView(

        @Schema(description = "Идентификатор игрока", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String uid,

        @Schema(description = "Ник для показа из карточки игрока: копий ника в команде больше "
                + "нет — они устаревали в тот же миг, когда игрок менял имя", example = "vasya")
        String nickname,

        @Schema(description = "Аватар как data-URL; null — аватара нет",
                example = "data:image/webp;base64,UklGRhIAAABXRUJQ", nullable = true)
        String avatarDataUrl) {
}
