package ru.hothat.friend.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Принятая заявка.
 *
 * <p>У отказа тела нет, а у согласия есть: согласие создаёт дружбу, и клиенту
 * нужно, кем именно он теперь дружит, — экран рисует уведомление до того, как
 * перечитает список друзей.
 */
@Schema(description = "Итог принятия заявки в друзья")
public record AcceptedFriendRequestResponseDTO(

        @Schema(description = "Идентификатор принятой заявки", example = "8123", type = "integer")
        long requestId,

        @Schema(description = "Новый друг: тот, кто отправлял заявку",
                example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk", pattern = "^[A-Za-z0-9]{28}$")
        String friendUid,

        @Schema(description = "Разрешённый ник нового друга", example = "vasya")
        String friendNickname) {
}
