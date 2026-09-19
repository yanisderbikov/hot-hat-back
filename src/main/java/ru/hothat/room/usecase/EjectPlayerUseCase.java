package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.domain.RoomPhase;

/**
 * Удалить участника из комнаты решением хозяина.
 *
 * <p>Только до начала партии: выгнать человека из идущей игры значит оставить
 * его команду без пары и обнулить чужой вечер. Уход из партии — это отдельное
 * событие с другими последствиями, и живёт оно в области партии.
 *
 * <p>Себя удалить нельзя, и это конфликт состояния, а не отказ в правах:
 * хозяин имеет право удалять, просто не себя. Ему нужен выход из комнаты или
 * передача хозяйства — разные кнопки с разными последствиями.
 *
 * <p>Повторное удаление уже ушедшего ничего не меняет и не считается ошибкой:
 * удаление идемпотентно, а хозяин мог нажать по устаревшему списку.
 */
@Service
@RequiredArgsConstructor
public class EjectPlayerUseCase {

    private final RoomAccessGuard roomAuthz;
    private final SaverRoom saverRoom;
    private final RoomSeats roomSeats;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void run(HotHatUser user, String roomId, String targetUid) {
        Room room = roomAuthz.requireHostRoomForWrite(user, roomId);
        if (targetUid.equals(user.uid())) {
            throw ApiException.of("KICK_SELF_FORBIDDEN", 409);
        }
        if (!RoomPhase.fromWire(room.getPhase()).isSetup()) {
            throw ApiException.of("GAME_ALREADY_STARTED", 409);
        }
        roomSeats.removeFromTeams(roomId, targetUid);
        saverRoom.deletePlayer(roomId, targetUid);
        saverRoom.deleteSpectator(roomId, targetUid);
    }
}
