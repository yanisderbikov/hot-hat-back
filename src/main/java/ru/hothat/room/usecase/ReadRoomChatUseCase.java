package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.RoomChatMessage;
import ru.hothat.room.api.dto.RoomChatMessageView;
import ru.hothat.room.api.dto.RoomChatPageResponseDTO;
import ru.hothat.room.api.dto.RoomChatQueryDTO;
import ru.hothat.room.store.RoomChatMessages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Прочитать чат комнаты.
 *
 * <p>Заменяет живую подписку браузера на {@code rooms/{id}/chat}
 * ({@code app-core.js:8795}). Подписка тянула ленту целиком — вместе со
 * встроенными фотографиями по сто с лишним килобайт каждая — и делала это
 * заново при каждом переподключении.
 *
 * <p>Выборка идёт от новых к старым: на экране нужен хвост ленты, а не начало.
 * Наружу список отдаётся перевёрнутым — в порядке показа, чтобы клиент не
 * переворачивал его у себя и не расходился с каналом, который присылает
 * сообщения по одному.
 *
 * <p>Курсор — момент отправки, а не смещение: в чате идущей партии сообщения
 * появляются между двумя запросами, и смещение показывало бы одно и то же
 * дважды.
 */
@Service
@RequiredArgsConstructor
public class ReadRoomChatUseCase {

    private final RoomAccessGuard roomAuthz;
    private final RoomChatMessages chatMessages;
    private final RoomProjections projections;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public RoomChatPageResponseDTO run(HotHatUser user, String roomId, RoomChatQueryDTO query) {
        roomAuthz.requireSeat(user, roomId);
        int limit = query == null ? RoomChatQueryDTO.DEFAULT_LIMIT : query.limitOrDefault();
        Long before = query == null ? null : query.before();

        PageRequest page = PageRequest.ofSize(limit);
        List<RoomChatMessage> newestFirst = before == null
                ? chatMessages.latest(roomId, page)
                : chatMessages.before(roomId, before, page);

        List<RoomChatMessageView> items = new ArrayList<>(newestFirst.size());
        for (RoomChatMessage message : newestFirst) {
            items.add(projections.chatMessage(message));
        }
        Collections.reverse(items);

        // Курсор выдаётся только у полной страницы: неполная означает, что
        // история кончилась, и предлагать листать дальше некуда.
        Long nextCursor = newestFirst.size() < limit || items.isEmpty()
                ? null : items.get(0).createdAtMs();
        return new RoomChatPageResponseDTO(items, nextCursor, limit);
    }
}
