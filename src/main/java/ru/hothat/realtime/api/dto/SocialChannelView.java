package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.conference.api.dto.ConferenceInviteView;

import java.util.List;

/**
 * Личное социальное состояние игрока — то, чем живёт шапка портала.
 *
 * <p>Общая проекция обоих кадров канала {@code /ws/v2/me/social}:
 * приветственного и обновляющего.
 *
 * <p>Здесь сходятся четыре прежние подписки ({@code realtime-social.js:156},
 * {@code :158}, {@code :159}, {@code :161}). Именно из них шапка считала свои
 * значки: непрочитанные сообщения, ждущие ответа заявки и принятые заявки,
 * которых игрок ещё не видел. Значки поэтому и приезжают первым же кадром —
 * до него портал рисовал их пустыми и дорисовывал, когда подписки оживали.
 *
 * <p>Каждое поле — форма своего адреса HTTP, поле в поле. Своей формы кадр не
 * изобретает ни одной.
 */
@Schema(description = "Заявки, входящие и бан слушателя")
public record SocialChannelView(

        @Schema(description = "Заявки, ждущие ответа игрока")
        FriendRequestInboxView incoming,

        @Schema(description = "Заявки, отправленные игроком")
        FriendRequestOutboxView outgoing,

        @Schema(description = "Входящие: непрочитанное, последнее письмо, последнее приглашение")
        SocialInboxView inbox,

        @Schema(description = "Состояние бана: канал закрывается сразу, как только он наступил")
        SocialBanView ban,

        @Schema(description = "Приглашения в видео-чаты, ждущие ответа: карточка «Вступить / Отклонить» "
                + "на любой странице; та же форма, что у GET /api/v2/conference/invites")
        List<ConferenceInviteView> conferenceInvites) {
}
