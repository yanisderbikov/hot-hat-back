package ru.hothat.chat.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.chat.api.dto.ChatRecordingAttachmentView;
import ru.hothat.chat.api.dto.ShareRecordingInChatRequestDTO;
import ru.hothat.chat.api.dto.SharedRecordingInChatResponseDTO;
import ru.hothat.chat.store.DirectChatStore;
import ru.hothat.config.HotHatUser;

import java.util.Map;

/**
 * Поделиться с собеседником записью игры.
 *
 * <p>Отправкой дело не ограничивается: собеседник добавляется в список тех,
 * кому запись открыта, — иначе карточка в переписке была бы кнопкой, ведущей
 * в отказ. Обе записи идут одной транзакцией.
 */
@Service
@RequiredArgsConstructor
public class ShareRecordingInChatUseCase {

    private final ChatPeerGuard peerGuard;
    private final SharedRecordingAccess recordings;
    private final ChatMessageSender sender;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public SharedRecordingInChatResponseDTO run(HotHatUser user, String peerUid,
                                                ShareRecordingInChatRequestDTO request) {
        String peer = peerGuard.requirePeer(user, peerUid);
        ChatRecordingAttachmentView attachment = recordings.share(user, request.recordingId(), peer);
        String caption = ChatMessageSender.normalized(request.caption());
        return new SharedRecordingInChatResponseDTO(sender.send(user, peer,
                DirectChatStore.NewMessage.recording(
                        caption.isEmpty() ? null : caption, attachment.recordingId()),
                Map.of(attachment.recordingId(), attachment)));
    }
}
