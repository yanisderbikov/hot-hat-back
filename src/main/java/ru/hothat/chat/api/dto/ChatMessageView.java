package ru.hothat.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Одно сообщение личной переписки так, как его показывают собеседникам.
 *
 * <p>Общая проекция: включается полем и в историю, и в ответы трёх операций
 * отправки, поэтому форма сообщения описана один раз.
 *
 * <p>Условное выражено nullable-полями, а не разными объектами: у старого
 * ответа ключи {@code roomInviteId}, {@code roomId}, {@code roomName} и
 * {@code roomInviteGameNumberAtInvite} появлялись только у приглашений, а
 * {@code attachment} был то картинкой, то записью под одним именем. Здесь
 * заполнено ровно одно из трёх полей — то, которое называет {@link #kind()}.
 */
@Schema(description = "Сообщение личной переписки")
public record ChatMessageView(

        @Schema(description = "Идентификатор сообщения: по нему клиент отличает своё эхо от нового",
                example = "184203", type = "integer")
        long id,

        @Schema(description = "Вид сообщения; он же говорит, какое из полей вложения заполнено")
        ChatMessageKind kind,

        @Schema(description = "Кто отправил", example = "8f3a2b1cQ7dE4rT6yU8iO0pA1sD2")
        String fromUid,

        @Schema(description = "Как звали отправителя в момент отправки; ник мог с тех пор смениться",
                example = "Vasya", nullable = true)
        String fromNickname,

        @Schema(description = "Кому отправлено", example = "2c9d4e7aP3lM5nB8vC1xZ0qW6eR4")
        String toUid,

        @Schema(description = "Текст сообщения; null — сообщение состоит из одного вложения",
                example = "Забирай запись, там на пятой минуте огонь", nullable = true)
        String text,

        @Schema(description = "Момент отправки, миллисекунды эпохи", example = "1788600000000", type = "integer")
        long createdAtMs,

        @Schema(description = "Вложенная фотография; заполнено только при kind=image", nullable = true)
        ChatImageAttachmentView image,

        @Schema(description = "Вложенная запись игры; заполнено только при kind=recording", nullable = true)
        ChatRecordingAttachmentView recording,

        @Schema(description = "Приглашение в комнату; заполнено только при kind=room_invite", nullable = true)
        ChatRoomInviteView roomInvite) {
}
