package ru.hothat.chat.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.chat.api.dto.SendChatTextRequestDTO;
import ru.hothat.chat.api.dto.SentChatTextResponseDTO;
import ru.hothat.chat.store.DirectChatStore;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;

import java.util.Map;

/** Отправить собеседнику текстовое сообщение. */
@Service
@RequiredArgsConstructor
public class SendChatTextUseCase {

    private final ChatPeerGuard peerGuard;
    private final ChatMessageSender sender;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public SentChatTextResponseDTO run(HotHatUser user, String peerUid, SendChatTextRequestDTO request) {
        String peer = peerGuard.requirePeer(user, peerUid);
        String text = ChatMessageSender.normalized(request.text());
        // Пустой текст не пройдёт и проверку тела запроса; здесь ловится
        // сообщение из одних пробелов, которое после нормализации исчезает.
        if (text.isEmpty()) {
            throw ApiException.of("MESSAGE_EMPTY");
        }
        return new SentChatTextResponseDTO(sender.send(user, peer,
                DirectChatStore.NewMessage.plain(DirectChatStore.TEXT, text, null), Map.of()));
    }
}
