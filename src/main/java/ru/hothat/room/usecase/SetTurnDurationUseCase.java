package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.SetTurnDurationRequestDTO;
import ru.hothat.room.api.dto.TurnDurationResponseDTO;
import ru.hothat.room.domain.RoomPhase;

/**
 * Задать длительность хода.
 *
 * <p>Только до начала партии: длительность заморожена вместе с составами, и
 * поменять её посреди игры значило бы дать одной команде больше времени, чем
 * другой.
 *
 * <p>Пишутся обе колонки — целая и дробная. Дробная появилась позже и
 * побеждает при расчёте дедлайна; оставить её нетронутой значило бы записать
 * новое значение, которое ни на что не влияет.
 */
@Service
@RequiredArgsConstructor
public class SetTurnDurationUseCase {

    private final RoomAccessGuard roomAuthz;
    private final SaverRoom saverRoom;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public TurnDurationResponseDTO run(HotHatUser user, String roomId,
                                       SetTurnDurationRequestDTO request) {
        Room room = roomAuthz.requireHostRoomForWrite(user, roomId);
        if (!RoomPhase.fromWire(room.getPhase()).isSetup()) {
            throw ApiException.of("ROOM_SETUP_ONLY", 409);
        }
        room.setTurnDuration(request.seconds());
        saverRoom.save(room);
        return new TurnDurationResponseDTO(roomId, request.seconds());
    }
}
