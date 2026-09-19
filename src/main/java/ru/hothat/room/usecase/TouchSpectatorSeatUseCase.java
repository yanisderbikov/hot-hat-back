package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.RoomSpectator;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.SpectatorHeartbeatResponseDTO;
import ru.hothat.room.domain.RoomPresence;

/**
 * Отметить, что зритель ещё смотрит.
 *
 * <p>Окно живости у зрителя своё — семь минут против пяти у игрока. Разница не
 * случайна: игрок, выпавший из окна, теряет место в команде, а зритель — всего
 * лишь строку в счётчике над столом, и щедрость здесь ничего не ломает.
 */
@Service
@RequiredArgsConstructor
public class TouchSpectatorSeatUseCase {

    private final RoomAccessGuard roomAuthz;
    private final SaverRoom saverRoom;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public SpectatorHeartbeatResponseDTO run(HotHatUser user, String roomId) {
        RoomSpectator spectator = roomAuthz.requireSpectator(user, roomId);
        long now = System.currentTimeMillis();
        spectator.setLastSeenAt(now);
        saverRoom.saveSpectator(spectator);
        return new SpectatorHeartbeatResponseDTO(now, now, RoomPresence.SPECTATOR_WINDOW_MS);
    }
}
