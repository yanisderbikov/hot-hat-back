package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.chat.api.dto.InboxLastMessageView;
import ru.hothat.chat.api.dto.InboxRoomInviteView;

/**
 * Входящие игрока — та же форма, что у {@code GET /api/v2/chat/inbox}.
 *
 * <p>Заменяет живую подписку на документ {@code socialInboxes/{uid}}
 * ({@code realtime-social.js:159}). Отсюда шапка портала берёт число
 * непрочитанного, а всплывающие уведомления — повод показаться: новое
 * сообщение и новое приглашение в комнату.
 *
 * <p>Строки может не быть вовсе: игроку ещё не писали. Тогда приезжают нули и
 * пустые поля — отдельного «не найдено» нет, для интерфейса это одно и то же.
 */
@Schema(description = "Входящие игрока")
public record SocialInboxView(

        @Schema(description = "Версия сообщений: растёт с каждым входящим", example = "42", type = "integer")
        long messageVersion,

        @Schema(description = "Сколько сообщений не прочитано во всех переписках вместе",
                example = "3", type = "integer")
        int unreadMessages,

        @Schema(description = "Последнее входящее сообщение; null — писем ещё не было", nullable = true)
        InboxLastMessageView lastMessage,

        @Schema(description = "Версия приглашений: растёт с каждым новым приглашением в комнату",
                example = "7", type = "integer")
        long roomInviteVersion,

        @Schema(description = "Последнее приглашение в комнату; null — приглашений не было", nullable = true)
        InboxRoomInviteView roomInvite,

        @Schema(description = "Когда входящие последний раз менялись, миллисекунды эпохи",
                example = "1788600000000", type = "integer")
        long updatedAtMs) {
}
