package ru.hothat.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Входящие игрока: одно число для значка и два повода показать уведомление.
 *
 * <p>Заменяет живую подписку браузера на документ {@code socialInboxes/{uid}}
 * ({@code realtime-social.js:111}). Отдаётся только своя строка — чужие
 * входящие не читает никто.
 *
 * <p>Строки может не быть вовсе: у игрока, которому ещё не писали, ответ
 * приходит с нулями и пустыми полями. Отдельного «не найдено» нет намеренно —
 * пустые входящие и отсутствующие входящие для интерфейса одно и то же.
 */
@Schema(description = "Входящие игрока")
public record SocialInboxResponseDTO(

        @Schema(description = "Версия сообщений: растёт на единицу с каждым входящим. "
                + "Клиенту она нужна, чтобы отличить новое событие от повторного чтения",
                example = "42", type = "integer")
        long messageVersion,

        @Schema(description = "Сколько сообщений не прочитано во всех переписках вместе", example = "3", type = "integer")
        int unreadMessages,

        @Schema(description = "Последнее входящее сообщение; null — писем ещё не было", nullable = true)
        InboxLastMessageView lastMessage,

        @Schema(description = "Версия приглашений: растёт с каждым новым приглашением в комнату",
                example = "7", type = "integer")
        long roomInviteVersion,

        @Schema(description = "Последнее приглашение в комнату; null — приглашений не было",
                nullable = true)
        InboxRoomInviteView roomInvite,

        @Schema(description = "Когда входящие последний раз менялись, миллисекунды эпохи",
                example = "1788600000000", type = "integer")
        long updatedAtMs) {
}
