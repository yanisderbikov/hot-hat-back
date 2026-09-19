package ru.hothat.chat.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.chat.api.dto.SendChatImageRequestDTO;
import ru.hothat.chat.api.dto.SentChatImageResponseDTO;
import ru.hothat.chat.store.DirectChatStore;
import ru.hothat.config.HotHatUser;

import java.util.Map;

/**
 * Отправить собеседнику фотографию.
 *
 * <p>Картинка ложится отдельной строкой {@code v2.direct_chat_photo}, а не
 * полем сообщения: data-URL весит до 120 000 символов, и в строке сообщения
 * он заставлял бы любое чтение истории поднимать из TOAST все картинки
 * страницы — даже когда рисуется список превью.
 */
@Service
@RequiredArgsConstructor
public class SendChatImageUseCase {

    private final ChatPeerGuard peerGuard;
    private final ChatMessageSender sender;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public SentChatImageResponseDTO run(HotHatUser user, String peerUid, SendChatImageRequestDTO request) {
        String peer = peerGuard.requirePeer(user, peerUid);
        // Формат, размер и границы сторон проверены на самом запросе: то, что
        // старый движок ловил уже внутри и отвечал 413, теперь не доезжает
        // до сценария вовсе.
        String caption = ChatMessageSender.normalized(request.caption());
        DirectChatStore.PhotoRow photo = new DirectChatStore.PhotoRow(
                request.dataUrl(), request.width(), request.height(),
                request.name() == null || request.name().isBlank() ? null : request.name());
        return new SentChatImageResponseDTO(sender.send(user, peer,
                DirectChatStore.NewMessage.plain(DirectChatStore.IMAGE,
                        caption.isEmpty() ? null : caption, photo),
                Map.of()));
    }
}
