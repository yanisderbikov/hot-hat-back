package ru.hothat.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Последнее приглашение в комнату, пришедшее игроку.
 *
 * <p>Общая проекция входящих. Живёт здесь, а не в области комнаты, потому
 * что это копия для всплывающего уведомления: сам список приглашений и их
 * судьба принадлежат комнате.
 */
@Schema(description = "Последнее приглашение в комнату")
public record InboxRoomInviteView(

        @Schema(description = "Идентификатор приглашения", example = "inv-7b2e5480a1c93d6f")
        String inviteId,

        @Schema(description = "Комната, куда зовут", example = "hat-0f3a9c1d7b2e5480")
        String roomId,

        @Schema(description = "Название комнаты", example = "Пятничная шляпа", nullable = true)
        String roomName,

        @Schema(description = "Кто позвал", example = "2c9d4e7aP3lM5nB8vC1xZ0qW6eR4", nullable = true)
        String fromUid,

        @Schema(description = "Как показать имя позвавшего", example = "Petya", nullable = true)
        String fromNickname,

        /**
         * Строкой, а не перечислением, намеренно: жизненный цикл приглашения
         * принадлежит области комнаты, здесь лежит его копия для уведомления.
         * Второй список тех же значений разошёлся бы с первым.
         */
        @Schema(description = "Судьба приглашения; уведомление показывают только у pending. "
                + "Сгоревшее приглашение остаётся здесь как pending — срок жизни считает область комнаты",
                example = "pending",
                allowableValues = {"pending", "accepted"},
                nullable = true)
        String status) {
}
