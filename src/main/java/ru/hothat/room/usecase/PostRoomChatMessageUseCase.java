package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.RoomChatMessage;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.PostRoomChatMessageRequestDTO;
import ru.hothat.room.api.dto.RoomChatMessageResponseDTO;

/**
 * Написать в чат комнаты.
 *
 * <p>Автора, его имя и место ставит сервер — из токена и из строки места.
 * Раньше их присылал браузер вместе с текстом ({@code sendChatMessage()},
 * {@code app-core.js:8610}), а путь {@code rooms/…/chat} в проверке прав на
 * запись пуст (находка A1): подписаться в чате комнаты чужим именем мог кто
 * угодно, включая имя хозяина.
 *
 * <p>Имя — снимок на момент отправки, а не ссылка на карточку: человек может
 * сменить ник посреди партии, и переписывать за него уже сказанное неверно.
 *
 * <p>Замок комнаты не берётся: сообщение — новая строка, никем больше не
 * правимая, и выстраивать в очередь весь стол ради реплики незачем.
 */
@Service
@RequiredArgsConstructor
public class PostRoomChatMessageUseCase {

    private final RoomAccessGuard roomAuthz;
    private final SaverRoom saverRoom;
    private final RoomProjections projections;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public RoomChatMessageResponseDTO run(HotHatUser user, String roomId,
                                          PostRoomChatMessageRequestDTO request) {
        RoomAccessGuard.Seat seat = roomAuthz.requireSeat(user, roomId);
        RoomChatMessage message = RoomChatEntries.newMessage(roomId, seat)
                .text(request.text().replaceAll("\\s+", " ").trim())
                .build();
        saverRoom.saveChatMessage(message);
        return new RoomChatMessageResponseDTO(projections.chatMessage(message));
    }
}
