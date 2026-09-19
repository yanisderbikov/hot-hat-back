package ru.hothat.chat.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.chat.api.dto.InboxLastMessageView;
import ru.hothat.chat.api.dto.InboxRoomInviteView;
import ru.hothat.chat.api.dto.SocialInboxResponseDTO;
import ru.hothat.chat.store.DirectChatStore;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.social.SocialInbox;
import ru.hothat.repository.GetterSocial;

import java.util.List;
import java.util.Optional;

/**
 * Входящие игрока: значок непрочитанного и поводы показать уведомление.
 *
 * <p>Считается на чтении, а не хранится отдельной строкой. Прежний
 * {@code socialInboxes/{uid}} был проекцией, которую писали все подряд —
 * отправка сообщения, отметка прочтения, приглашение в комнату, — и любая
 * пропущенная запись оставляла значок висеть навсегда. Теперь непрочитанное
 * — это сумма по своим строкам участия, а последнее письмо — самое свежее
 * входящее сообщение: расходиться тут нечему.
 *
 * <p>{@code messageVersion} — идентификатор последнего входящего. Он строго
 * растёт (его выдаёт база) и меняется ровно тогда, когда приходит письмо, то
 * есть отвечает на тот же вопрос, что и прежний счётчик: «это новое событие
 * или я перечитал старое». Разница в том, что счётчик приходилось хранить и
 * увеличивать руками.
 *
 * <p>Половина про приглашение в комнату по-прежнему читается из старой строки:
 * приглашения заводит область {@code room}, а она ещё не переехала. Когда
 * переедет — приглашение станет обычным сообщением переписки, и этот кусок
 * уйдёт вместе с чтением старой таблицы.
 */
@Service
@RequiredArgsConstructor
public class GetMySocialInboxUseCase {

    private final DirectChatStore chats;
    private final ChatPeerDirectory peerDirectory;
    private final ChatMessageAssembler assembler;
    private final GetterSocial getterSocial;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public SocialInboxResponseDTO run(HotHatUser user) {
        Optional<DirectChatStore.MessageRow> incoming = chats.lastIncoming(user.uid());

        long messageVersion = 0L;
        long lastMessageAtMs = 0L;
        InboxLastMessageView lastMessage = null;
        if (incoming.isPresent()) {
            DirectChatStore.MessageRow row = incoming.get();
            messageVersion = row.id();
            lastMessageAtMs = row.createdAtMs();
            lastMessage = new InboxLastMessageView(
                    row.fromUid(),
                    peerDirectory.load(List.of(row.fromUid())).nickname(row.fromUid()),
                    assembler.preview(row),
                    row.createdAtMs());
        }

        SocialInbox legacy = getterSocial.getInbox(user.uid()).orElse(null);
        String inviteId = legacy == null ? null : legacy.getRoomInviteId();
        InboxRoomInviteView roomInvite = inviteId == null || inviteId.isBlank() ? null
                : new InboxRoomInviteView(
                        inviteId,
                        legacy.getRoomInviteRoomId(),
                        blankToNull(legacy.getRoomInviteRoomName()),
                        blankToNull(legacy.getRoomInviteFromUid()),
                        blankToNull(legacy.getRoomInviteFromNickname()),
                        blankToNull(legacy.getRoomInviteStatus()));
        long roomInviteVersion = legacy == null || legacy.getRoomInviteVersion() == null
                ? 0L : legacy.getRoomInviteVersion();
        long legacyUpdatedAtMs = legacy == null || legacy.getUpdatedAt() == null
                ? 0L : legacy.getUpdatedAt().toEpochMilli();

        return new SocialInboxResponseDTO(
                messageVersion,
                chats.totalUnread(user.uid()),
                lastMessage,
                roomInviteVersion,
                roomInvite,
                Math.max(lastMessageAtMs, legacyUpdatedAtMs));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
