package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.RoomChatMessage;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.EditRoomChatMessageRequestDTO;
import ru.hothat.room.api.dto.EditedRoomChatMessageResponseDTO;
import ru.hothat.room.store.RoomChatMessages;

/**
 * Исправить своё сообщение в чате комнаты.
 *
 * <p>Авторство — инвариант сценария, а не предикат на классе. Это один из пяти
 * случаев, названных в §7.4 плана честно: проверка требует чтения самой
 * строки, и предикат уровня класса читал бы её вторично. На классе стоит более
 * широкое право — участник или зритель комнаты.
 *
 * <p>Правится только текст. Подменить фотографию нельзя: соседи по столу уже
 * увидели ту, что была, и «правка» превратилась бы в подлог.
 *
 * <p>Отметки «исправлено» хранилище не держит, и выдумывать её здесь нельзя:
 * поле, которое сервер не может заполнить правдиво, — это ложь в схеме.
 * Отметка появится вместе с колонкой.
 */
@Service
@RequiredArgsConstructor
public class EditRoomChatMessageUseCase {

    private final RoomAccessGuard roomAuthz;
    private final RoomChatMessages chatMessages;
    private final SaverRoom saverRoom;
    private final RoomProjections projections;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public EditedRoomChatMessageResponseDTO run(HotHatUser user, String roomId, String messageId,
                                                EditRoomChatMessageRequestDTO request) {
        roomAuthz.requireSeat(user, roomId);
        RoomChatMessage message = chatMessages.find(roomId, messageId)
                .orElseThrow(() -> ApiException.of("CHAT_MESSAGE_NOT_FOUND", 404));
        if (!user.uid().equals(message.getUid())) {
            throw ApiException.of("NOT_CHAT_AUTHOR", 403);
        }
        if (message.getAttachment() != null) {
            // Тело безупречно, но выполнить нечего: у сообщения с вложением
            // править нечего, кроме самого вложения, а это запрещено.
            throw ApiException.of("CHAT_IMAGE_NOT_EDITABLE", 422);
        }
        message.setText(request.text().replaceAll("\\s+", " ").trim());
        saverRoom.saveChatMessage(message);
        return new EditedRoomChatMessageResponseDTO(projections.chatMessage(message));
    }
}
