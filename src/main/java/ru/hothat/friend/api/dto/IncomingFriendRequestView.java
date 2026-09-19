package ru.hothat.friend.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Входящая заявка так, как её рисует экран друзей.
 *
 * <p>Поля {@code status} здесь нет намеренно: адрес отдаёт только ждущие
 * ответа заявки ({@code SocialManager.getIncomingRequests} спрашивает
 * хранилище со статусом {@code pending}), и nullable-поле, у которого одно
 * значение, — это шум, а не сведения.
 */
@Schema(description = "Входящая заявка в друзья")
public record IncomingFriendRequestView(

        @Schema(description = "Идентификатор заявки: им отвечают на неё", example = "8123", type = "integer")
        long id,

        @Schema(description = "Кто зовёт в друзья", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk",
                pattern = "^[A-Za-z0-9]{28}$")
        String fromUid,

        @Schema(description = "Разрешённый ник отправителя", example = "vasya")
        String fromNickname,

        @Schema(description = "Аватар отправителя как data-URL; null — аватара нет",
                example = "data:image/webp;base64,UklGRh4AAABXRUJQ", nullable = true)
        String fromAvatarDataUrl,

        @Schema(description = "Когда заявку отправили, миллисекунды эпохи", example = "1788600000000", type = "integer")
        long createdAtMs) {
}
