package ru.hothat.profile.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.common.api.ErrorCode;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.ActiveRoomResponseDTO;
import ru.hothat.profile.api.dto.SetActiveRoomRequestDTO;
import ru.hothat.profile.store.ProfileStore;
import ru.hothat.room.spi.RoomDirectoryPort;

/**
 * Отметить комнату, в которой игрок сейчас играет.
 *
 * <p>Заменяет прямую запись браузера в {@code users/{uid}.activeRoomId}.
 * Метка нужна ровно для одного: вернуть человека в идущую партию после
 * перезагрузки вкладки, когда локальная память браузера пуста — новое
 * устройство, режим инкогнито, очищенный кеш.
 *
 * <p>Существование комнаты проверяется, хотя браузер не проверял. Метка ведёт
 * автоматический вход при следующем запуске, и указывающая в никуда метка —
 * это ровно тот фантомный экран, который недавно чинили на стороне клиента
 * (SCRUM-11). Дешевле не дать записать мусор, чем потом от него убегать.
 *
 * <p>Время ставит сервер: браузер писал сюда свои часы, а по ним нельзя
 * отличить свежую метку от забытой в прошлом месяце.
 */
@Service
@RequiredArgsConstructor
public class SetMyActiveRoomUseCase {

    private final ProfileStore profiles;
    /** «Есть ли такая комната» спрашиваем у её области, а не у её таблицы. */
    private final RoomDirectoryPort rooms;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public ActiveRoomResponseDTO run(HotHatUser user, SetActiveRoomRequestDTO request) {
        if (!rooms.exists(request.roomId())) {
            throw ErrorCode.ROOM_NOT_FOUND.raise();
        }
        long markedAt = profiles.setActiveRoom(user.uid(), request.roomId());
        return new ActiveRoomResponseDTO(request.roomId(), markedAt);
    }
}
