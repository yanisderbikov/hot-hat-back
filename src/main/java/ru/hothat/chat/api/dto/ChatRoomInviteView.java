package ru.hothat.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Приглашение в комнату, приехавшее сообщением в переписке.
 *
 * <p>Общая проекция: включается полем в ответы разных операций. Само
 * приглашение создаёт область {@code room}; переписка лишь показывает его
 * и потому знает о нём ровно столько, сколько нужно для кнопки «войти».
 */
@Schema(description = "Приглашение в комнату внутри переписки")
public record ChatRoomInviteView(

        @Schema(description = "Идентификатор приглашения: с ним игрок входит в комнату",
                example = "inv-7b2e5480a1c93d6f")
        String inviteId,

        @Schema(description = "Комната, куда зовут", example = "hat-0f3a9c1d7b2e5480")
        String roomId,

        @Schema(description = "Название комнаты на момент приглашения", example = "Пятничная шляпа",
                nullable = true)
        String roomName,

        @Schema(description = "Номер партии на момент приглашения; null — в старых сообщениях его нет",
                example = "3", type = "integer", nullable = true)
        Integer gameNumberAtInvite) {
}
