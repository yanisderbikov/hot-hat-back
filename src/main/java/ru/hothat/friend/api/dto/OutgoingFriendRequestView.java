package ru.hothat.friend.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Своя заявка в друзья: и ждущая ответа, и уже принятая.
 *
 * <p>Сегодня это два разных массива в одном ответе — {@code outgoingRequests}
 * с ником и аватаром и {@code acceptedOutgoing} из трёх полей. Клиенту
 * приходится знать оба и различать по имени ключа. Здесь список один,
 * а необязательное выражено nullable-полями.
 */
@Schema(description = "Исходящая заявка в друзья")
public record OutgoingFriendRequestView(

        @Schema(description = "Идентификатор заявки", example = "8123", type = "integer")
        long id,

        @Schema(description = "Кому ушла заявка", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk",
                pattern = "^[A-Za-z0-9]{28}$")
        String toUid,

        @Schema(description = "Состояние заявки")
        FriendRequestStatus status,

        @Schema(description = "Разрешённый ник адресата; null у принятой заявки — движок его не отдаёт",
                example = "vasya", nullable = true)
        String toNickname,

        @Schema(description = "Аватар адресата как data-URL; null — аватара нет или заявка уже принята",
                example = "data:image/webp;base64,UklGRh4AAABXRUJQ", nullable = true)
        String toAvatarDataUrl,

        @Schema(description = "Когда заявку отправили, миллисекунды эпохи; 0 у принятой заявки",
                example = "1788600000000", type = "integer")
        long createdAtMs,

        @Schema(description = "Когда на заявку ответили, миллисекунды эпохи; null — ответа ещё нет",
                example = "1788600300000", type = "integer", nullable = true)
        Long answeredAtMs) {
}
