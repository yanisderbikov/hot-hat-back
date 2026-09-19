package ru.hothat.realtime.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.auth.api.dto.MyBanStateResponseDTO;
import ru.hothat.auth.usecase.GetMyBanStateUseCase;
import ru.hothat.chat.api.dto.SocialInboxResponseDTO;
import ru.hothat.chat.usecase.GetMySocialInboxUseCase;
import ru.hothat.conference.usecase.ListMyConferenceInvitesUseCase;
import ru.hothat.config.HotHatUser;
import ru.hothat.friend.api.dto.IncomingFriendRequestsResponseDTO;
import ru.hothat.friend.api.dto.OutgoingFriendRequestsResponseDTO;
import ru.hothat.friend.usecase.ListIncomingFriendRequestsUseCase;
import ru.hothat.friend.usecase.ListOutgoingFriendRequestsUseCase;
import ru.hothat.realtime.api.dto.FriendRequestInboxView;
import ru.hothat.realtime.api.dto.FriendRequestOutboxView;
import ru.hothat.realtime.api.dto.SocialBanView;
import ru.hothat.realtime.api.dto.SocialChannelView;
import ru.hothat.realtime.api.dto.SocialInboxView;

/**
 * Показать личное социальное состояние слушателю канала {@code /ws/v2/me/social}.
 *
 * <p>Сценарий один и тот же для обоих кадров: и для приветственного, и для
 * каждого обновления канал спрашивает одно — «что сейчас у этого игрока с
 * заявками, входящими и баном». Различие между кадрами придумывает транспорт.
 *
 * <p>Все пять частей собирают те же сценарии, что отвечают на адреса HTTP.
 * Своей копии сборки здесь нет ни одной: шапка портала считает значки по
 * одним и тем же полям, откуда бы они ни приехали — кадром или ответом.
 *
 * <p>Чужого состояния канал не отдаёт: у каждого сценария внутри стоит «только
 * своё», и подставить сюда чужой идентификатор нечем — личность берётся из
 * рукопожатия.
 *
 * <p>Транзакция одна на пять чтений и {@code readOnly}: иначе значок
 * непрочитанного мог бы приехать из состояния до чужого письма, а последнее
 * сообщение — после.
 */
@Service
@RequiredArgsConstructor
public class StreamSocialChannelUseCase {

    private final ListIncomingFriendRequestsUseCase incomingRequests;
    private final ListOutgoingFriendRequestsUseCase outgoingRequests;
    private final GetMySocialInboxUseCase socialInbox;
    private final GetMyBanStateUseCase banState;
    private final ListMyConferenceInvitesUseCase conferenceInvites;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public SocialChannelView run(HotHatUser user) {
        IncomingFriendRequestsResponseDTO incoming = incomingRequests.run(user);
        OutgoingFriendRequestsResponseDTO outgoing = outgoingRequests.run(user);
        SocialInboxResponseDTO inbox = socialInbox.run(user);
        MyBanStateResponseDTO ban = banState.run(user);
        return new SocialChannelView(
                new FriendRequestInboxView(incoming.items(), incoming.nextCursor(), incoming.limit()),
                new FriendRequestOutboxView(outgoing.items(), outgoing.nextCursor(), outgoing.limit()),
                new SocialInboxView(inbox.messageVersion(), inbox.unreadMessages(), inbox.lastMessage(),
                        inbox.roomInviteVersion(), inbox.roomInvite(), inbox.updatedAtMs()),
                new SocialBanView(ban.banned(), ban.reason(), ban.bannedAtMs()),
                conferenceInvites.run(user).items());
    }
}
