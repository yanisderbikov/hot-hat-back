package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.repository.SaverRoom;

/**
 * Уйти из зала.
 *
 * <p>Комнату не убирает и не трогает: зритель её не наполнял и, уходя, не
 * опустошает. Уборка привязана к выходу игрока — там она и живёт.
 */
@Service
@RequiredArgsConstructor
public class LeaveSpectatorSeatUseCase {

    private final RoomAccessGuard roomAuthz;
    private final SaverRoom saverRoom;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void run(HotHatUser user, String roomId) {
        roomAuthz.requireSpectator(user, roomId);
        saverRoom.deleteSpectator(roomId, user.uid());
    }
}
