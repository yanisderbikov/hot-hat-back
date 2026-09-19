package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.RenameRoomRequestDTO;
import ru.hothat.room.api.dto.RoomNameResponseDTO;
import ru.hothat.util.Json;

/**
 * Переименовать комнату.
 *
 * <p>Право хозяйское: имя комнаты видно в витрине и уезжает в приглашения,
 * поэтому менять его должен один человек, а не любой из десяти.
 *
 * <p>Комната берётся под замком, хотя меняется одна колонка. Причина в
 * устройстве строки: у неё нет частичного обновления, и запись переименования
 * уносит в базу все колонки разом — вместе с фазой, счётом и составами,
 * прочитанными до чужой правки (находка B7). Замок делает такое затирание
 * невозможным.
 */
@Service
@RequiredArgsConstructor
public class RenameRoomUseCase {

    private final RoomAccessGuard roomAuthz;
    private final SaverRoom saverRoom;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public RoomNameResponseDTO run(HotHatUser user, String roomId, RenameRoomRequestDTO request) {
        Room room = roomAuthz.requireHostRoomForWrite(user, roomId);
        room.setName(Json.str(request.name().replaceAll("\\s+", " ").trim(), 80));
        saverRoom.save(room);
        return new RoomNameResponseDTO(roomId, room.getName());
    }
}
