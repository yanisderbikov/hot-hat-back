package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Отправленное приглашение в комнату.
 *
 * <p>Одна форма ответа на две ветки. Старый {@code send_room_invite} отвечал то
 * {@code {ok, alreadyInside}}, то {@code {ok, inviteId, roomId, roomName}}, и
 * клиент различал их по наличию ключа {@code inviteId} (замечание C6). Здесь
 * «он уже внутри» — обычное поле ответа, и приглашение в этом случае не
 * заводится: звать в комнату того, кто в ней сидит, незачем.
 *
 * <p>Приглашение и сообщение переписки пишутся одной транзакцией: карточка в
 * чате без приглашения — это кнопка, ведущая в отказ.
 */
@Schema(description = "Отправленное приглашение в комнату")
public record SentRoomInviteResponseDTO(

        @Schema(description = "Идентификатор приглашения; null — друг уже в комнате и звать некуда",
                example = "ri-3f9a2b1c7d8e0f4a", nullable = true)
        String inviteId,

        @Schema(description = "Комната", example = "hat-0f3a9c1d7b2e5480")
        String roomId,

        @Schema(description = "Название комнаты на момент приглашения", example = "Комната Васи")
        String roomName,

        @Schema(description = "Друг уже в комнате: приглашение не понадобилось",
                example = "false", type = "boolean")
        boolean alreadyInside,

        @Schema(description = "Когда приглашение перестанет действовать, миллисекунды эпохи; "
                + "null — приглашения нет", example = "1788686400000", type = "integer",
                nullable = true)
        Long expiresAtMs) {
}
