package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.RoomChatMessage;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.PostRoomChatImageRequestDTO;
import ru.hothat.room.api.dto.RoomChatImageResponseDTO;
import ru.hothat.util.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Отправить фотографию в чат комнаты.
 *
 * <p>Отдельный сценарий, а не ключ внутри отправки текста: у текста обязателен
 * текст, у фотографии — байты и размеры, и два набора обязательных полей одной
 * схемой не описываются. Раньше это был один вызов с произвольным
 * {@code attachment}, форму которого никто не проверял.
 *
 * <p>Размеры записываются вместе с байтами, чтобы получатель сверстал место
 * под картинку до её загрузки и лента не прыгала. Сжимает отправитель:
 * хранилища у чатовых фото нет, они лежат прямо в строке сообщения, и поэтому
 * ограничены по объёму.
 */
@Service
@RequiredArgsConstructor
public class PostRoomChatImageUseCase {

    private final RoomAccessGuard roomAuthz;
    private final SaverRoom saverRoom;
    private final RoomProjections projections;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public RoomChatImageResponseDTO run(HotHatUser user, String roomId,
                                        PostRoomChatImageRequestDTO request) {
        RoomAccessGuard.Seat seat = roomAuthz.requireSeat(user, roomId);

        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("kind", "image");
        attachment.put("dataUrl", request.dataUrl());
        attachment.put("width", request.width());
        attachment.put("height", request.height());
        attachment.put("name", Json.str(request.fileName(), 80));

        RoomChatMessage message = RoomChatEntries.newMessage(roomId, seat)
                // Пустой текст, а не null: колонка объявлена nullable, но
                // проекция различает «сообщение из одной картинки» именно по
                // пустоте, и хранить два способа сказать одно незачем.
                .text("")
                .attachment(attachment)
                .build();
        saverRoom.saveChatMessage(message);
        return new RoomChatImageResponseDTO(projections.chatMessage(message));
    }
}
