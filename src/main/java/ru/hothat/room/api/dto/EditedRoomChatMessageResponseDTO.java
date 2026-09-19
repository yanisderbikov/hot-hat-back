package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Исправленное сообщение чата комнаты.
 *
 * <p>Признака «исправлено» в проекции нет, и это не забывчивость: хранилище
 * такой отметки не держит, а вычислить её на лету нельзя. Заявить поле,
 * которое сервер заполнить не может, значило бы соврать в схеме. Отметка
 * появится вместе с колонкой.
 */
@Schema(description = "Исправленное сообщение чата")
public record EditedRoomChatMessageResponseDTO(

        @Schema(description = "Сообщение в новой редакции")
        RoomChatMessageView message) {
}
