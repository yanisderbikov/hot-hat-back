package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.LeftRoomResponseDTO;
import ru.hothat.room.domain.RoomPhase;
import ru.hothat.room.spi.RoomJanitorPort;

import java.util.Optional;

/**
 * Выйти из комнаты.
 *
 * <p>Заменяет три клиентских действия подряд: выход из команды
 * ({@code leaveTeam({deletePlayer:true})}), удаление своего места и
 * {@code POST /api/cleanup-rooms?room_id=} следом ({@code app-core.js:12290},
 * {@code :1045}). Последнее было вежливостью вкладки: закрытая мимо
 * обработчика браузером его не делала, и брошенная комната доживала до общего
 * прохода уборщика — до десяти минут в витрине на главной. Здесь уборка
 * опустевшей комнаты — инвариант самого выхода.
 *
 * <p>Из замороженного состава партии уходящий вычёркивается тоже. Иначе его
 * место продолжало бы получать ходы, а команда — ждать объясняющего, которого
 * нет.
 */
@Service
@RequiredArgsConstructor
public class LeaveRoomUseCase {

    private final RoomAccessGuard roomAuthz;
    private final SaverRoom saverRoom;
    private final RoomSeats roomSeats;
    private final RoomJanitorPort janitor;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public LeftRoomResponseDTO run(HotHatUser user, String roomId) {
        Room room = roomAuthz.requireRoomForWrite(roomId);
        roomAuthz.requireMember(user, roomId);

        Optional<RoomTeam> leftTeam = roomSeats.removeFromTeams(roomId, user.uid());
        if (!RoomPhase.fromWire(room.getPhase()).isSetup()) {
            roomSeats.removeFromFrozenRoster(room, user.uid());
            saverRoom.save(room);
        }
        saverRoom.deletePlayer(roomId, user.uid());
        saverRoom.deleteSpectator(roomId, user.uid());

        // Уборка идёт через того же уборщика, что и общий проход: две формулы
        // «комната опустела» разошлись бы на первой же правке порогов.
        boolean roomClosed = janitor.cleanupOne(roomId).deleted();
        return new LeftRoomResponseDTO(roomId, roomClosed,
                leftTeam.map(RoomTeam::getTeamId).orElse(null));
    }
}
