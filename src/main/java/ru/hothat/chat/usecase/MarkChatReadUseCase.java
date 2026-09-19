package ru.hothat.chat.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.chat.store.DirectChatStore;
import ru.hothat.config.HotHatUser;

/**
 * Отметить переписку прочитанной.
 *
 * <p>Ответа нет: старый {@code {"ok": true}} не нёс сведений, которых не было
 * бы в запросе, а новое число непрочитанного клиент и так возьмёт из входящих.
 * Адрес отвечает 204.
 *
 * <p>Обнуляется только своя строка участия. Раньше оба счётчика лежали одним
 * jsonb в шапке переписки, и «прочитал» одного мог затереть «получил» другого.
 */
@Service
@RequiredArgsConstructor
public class MarkChatReadUseCase {

    private final ChatPeerGuard peerGuard;
    private final DirectChatStore chats;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void run(HotHatUser user, String peerUid) {
        String peer = peerGuard.requirePeer(user, peerUid);
        // Переписки может не быть вовсе: читать нечего, отмечать нечего.
        chats.findThread(user.uid(), peer).ifPresent(chatId -> chats.markRead(chatId, user.uid()));
    }
}
