package ru.hothat.realtime.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.chat.api.dto.ChatHistoryPageResponseDTO;
import ru.hothat.chat.api.dto.ChatHistoryQueryDTO;
import ru.hothat.chat.usecase.ReadChatHistoryUseCase;
import ru.hothat.config.HotHatUser;
import ru.hothat.realtime.api.dto.DirectChatWindowView;

/**
 * Показать переписку слушателю канала {@code /ws/v2/chat/{peerUid}}.
 *
 * <p>Сценарий один и тот же для обоих кадров: и для приветственного, и для
 * каждого обновления канал спрашивает одно — «как выглядит переписка для
 * этого участника сейчас». Различие между кадрами придумывает транспорт,
 * а не сценарий.
 *
 * <p>Страницу собирает тот же сценарий, что отвечает на
 * {@code GET /api/v2/chat/{peerUid}/messages}, — вместе с проверкой права:
 * подписаться на переписку может только её участник, друг или напарник по
 * рейтинговой команде, и проверяется это на <b>каждом</b> чтении. Удалённый
 * из друзей перестаёт получать кадры сразу, а не когда закроет вкладку.
 *
 * <p>Свою копию сборки канал не держит намеренно: именно из-за двух источников
 * одного списка интерфейс когда-то и завёл две ветки отрисовки.
 *
 * <p>Область {@code realtime} зовёт сценарий области {@code chat} — то же
 * отступление от §7.3, что у канала {@code /ws/v2/me/social}, и по той же
 * причине: своя сборка была бы второй правдой о том же экране.
 */
@Service
@RequiredArgsConstructor
public class StreamDirectChatUseCase {

    private final ReadChatHistoryUseCase readHistory;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public DirectChatWindowView run(HotHatUser user, String peerUid) {
        // Тот же размер окна, что у страницы HTTP: канал и история не должны
        // показывать разное количество сообщений одному и тому же человеку.
        ChatHistoryPageResponseDTO page = readHistory.run(user, peerUid,
                new ChatHistoryQueryDTO(ChatHistoryQueryDTO.DEFAULT_LIMIT));
        return new DirectChatWindowView(page.peerUid(), page.peerNickname(), page.items(),
                page.nextCursor(), page.limit());
    }
}
