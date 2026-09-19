package ru.hothat.chat.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.chat.store.DirectChatStore;
import ru.hothat.chat.usecase.ChatPeerGuard;
import ru.hothat.model.social.SocialInbox;
import ru.hothat.repository.GetterSocial;
import ru.hothat.repository.SaverSocial;
import ru.hothat.util.Ids;
import ru.hothat.util.Json;

/**
 * Записи в переписку по просьбе соседей: реализация {@link ChatCommandPort}.
 *
 * <p>Транзакции своей не открывает: {@code MANDATORY} — она уже идёт у того,
 * кто просит. Это и есть смысл командного порта: приглашение и его карточка
 * ложатся вместе или не ложатся вовсе.
 *
 * <p>Строка входящих пишется здесь же, а не у просящего. Владелец инбокса —
 * переписка, и пока половина его полей живёт в прежней таблице, писать их
 * должна всё равно она: иначе у значка «вас позвали» оказалось бы два
 * писателя из разных областей.
 */
@Service
@RequiredArgsConstructor
public class ChatCommands implements ChatCommandPort {

    /** Столько символов сообщения помещается в превью списка переписок. */
    private static final int PREVIEW_LENGTH = 200;

    private final DirectChatStore chats;
    private final ChatPeerGuard peerGuard;
    private final GetterSocial getterSocial;
    private final SaverSocial saverSocial;
    /** Рассылка живых обновлений: слушателю событие достаётся после коммита. */
    private final ApplicationEventPublisher events;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String requireWritablePeer(String selfUid, String peerUid) {
        return peerGuard.requirePeer(selfUid, peerUid);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long appendRoomInvite(RoomInviteCard card) {
        long chatId = chats.openThread(card.fromUid(), card.toUid());
        DirectChatStore.MessageRow row = chats.append(chatId, card.fromUid(), card.toUid(),
                DirectChatStore.NewMessage.roomInvite(card.text(), card.inviteId()));
        bumpInbox(card, row.createdAtMs());
        // Публикуем внутри транзакции, но слушатель ждёт коммита: приглашения,
        // которого после отката не осталось, собеседник видеть не должен.
        events.publishEvent(new DirectChatChangedEvent(Ids.pair(card.fromUid(), card.toUid())));
        return row.id();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markRoomInviteAccepted(String uid, String inviteId) {
        getterSocial.getInbox(uid).ifPresent(inbox -> {
            if (inviteId.equals(inbox.getRoomInviteId())) {
                inbox.setRoomInviteStatus("accepted");
                saverSocial.saveInbox(inbox);
            }
        });
    }

    /**
     * Значок «вас позвали».
     *
     * <p>Половина строки входящих уже не пишется вовсе: последнее письмо и
     * счётчик непрочитанного считаются на чтении по таблицам v2. Здесь
     * остаются поля приглашения — их читателю больше взять неоткуда, пока
     * приглашения живут в прежней таблице.
     */
    private void bumpInbox(RoomInviteCard card, long nowMs) {
        SocialInbox inbox = getterSocial.getInbox(card.toUid())
                .orElseGet(() -> SocialInbox.builder().uid(card.toUid()).build());
        inbox.setRoomInviteVersion(Math.max(0, inbox.getRoomInviteVersion()) + 1);
        inbox.setRoomInviteId(card.inviteId());
        inbox.setRoomInviteRoomId(card.roomId());
        inbox.setRoomInviteRoomName(card.roomName());
        inbox.setRoomInviteFromUid(card.fromUid());
        inbox.setRoomInviteFromNickname(card.fromNickname());
        inbox.setRoomInviteStatus("pending");
        inbox.setLastMessageAtMs(nowMs);
        inbox.setLastMessageText(Json.str(card.text(), PREVIEW_LENGTH));
        saverSocial.saveInbox(inbox);
    }
}
