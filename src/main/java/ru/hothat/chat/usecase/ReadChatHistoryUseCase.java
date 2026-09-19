package ru.hothat.chat.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.chat.api.dto.ChatHistoryPageResponseDTO;
import ru.hothat.chat.api.dto.ChatHistoryQueryDTO;
import ru.hothat.chat.api.dto.ChatMessageView;
import ru.hothat.chat.api.dto.ChatRecordingAttachmentView;
import ru.hothat.chat.api.dto.ChatRoomInviteView;
import ru.hothat.chat.store.DirectChatStore;
import ru.hothat.config.HotHatUser;
import ru.hothat.room.spi.RoomInvitePort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Последние сообщения одной переписки.
 *
 * <p>Транзакция стала {@code readOnly}: старый движок заводил шапку переписки
 * прямо на чтении, то есть просмотр диалога был записью. Теперь шапку заводит
 * отправка, а у пары, которая ещё не переписывалась, история пуста — и это
 * ровно то, что показывает экран.
 *
 * <p>Четыре запроса на страницу независимо от её содержимого: сообщения, их
 * картинки, карточки поделённых записей и карточки приглашений в комнату.
 * Плюс один на профили двоих.
 */
@Service
@RequiredArgsConstructor
public class ReadChatHistoryUseCase {

    private final ChatPeerGuard peerGuard;
    private final DirectChatStore chats;
    private final ChatPeerDirectory peerDirectory;
    private final SharedRecordingAccess recordings;
    private final ChatMessageAssembler assembler;
    /** Комната и её название принадлежат соседней области; спрашиваем её порт. */
    private final RoomInvitePort roomInvites;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public ChatHistoryPageResponseDTO run(HotHatUser user, String peerUid, ChatHistoryQueryDTO query) {
        String peer = peerGuard.requirePeer(user, peerUid);
        int limit = query == null ? ChatHistoryQueryDTO.DEFAULT_LIMIT : query.limitOrDefault();

        List<DirectChatStore.MessageRow> rows = chats.findThread(user.uid(), peer)
                .map(chatId -> chats.history(chatId, user.uid(), peer, limit))
                .orElseGet(List::of);

        ChatPeerDirectory.Snapshot profiles = peerDirectory.load(List.of(user.uid(), peer));
        Map<String, ChatRecordingAttachmentView> attachments = recordings.describe(rows.stream()
                .map(DirectChatStore.MessageRow::recordingId)
                .filter(Objects::nonNull)
                .toList());

        Map<String, ChatRoomInviteView> invites = invites(rows);

        List<ChatMessageView> items = new ArrayList<>(rows.size());
        for (DirectChatStore.MessageRow row : rows) {
            items.add(assembler.view(row, profiles.nickname(row.fromUid()), attachments, invites));
        }
        return new ChatHistoryPageResponseDTO(peer, profiles.nickname(peer), items, null, limit);
    }

    /** Карточки приглашений страницы — одним вопросом области комнаты. */
    private Map<String, ChatRoomInviteView> invites(List<DirectChatStore.MessageRow> rows) {
        List<String> ids = rows.stream()
                .map(DirectChatStore.MessageRow::roomInviteId)
                .filter(Objects::nonNull)
                .toList();
        Map<String, ChatRoomInviteView> byId = new LinkedHashMap<>();
        roomInvites.cards(ids).forEach((inviteId, card) -> byId.put(inviteId, new ChatRoomInviteView(
                card.inviteId(), card.roomId(), card.roomName(), card.gameNumberAtInvite())));
        return byId;
    }
}
