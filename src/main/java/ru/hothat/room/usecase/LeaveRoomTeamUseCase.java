package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.domain.RoomPhase;
import ru.hothat.room.domain.RoomSeatingPolicy;

/**
 * Выйти из команды, оставшись в комнате.
 *
 * <p>Переезд {@code leaveTeam()} ({@code app-core.js:12641}) без ветки
 * {@code deletePlayer}: удаление места — это выход из комнаты, у него свой
 * адрес и свои последствия (уборка опустевшей комнаты), и склеивать их
 * флагом в теле значило описывать одной схемой две операции.
 *
 * <p>Составы фиксируются на всю партию: пересесть посреди игры нельзя.
 * Уходящему из комнаты это не мешает — его вычёркивают из состава в любой
 * фазе, иначе команда ждала бы объясняющего, которого нет.
 *
 * <p>Выход из команды, в которой человек не состоит, ничего не меняет и не
 * считается ошибкой: удаление идемпотентно, и повтор по разорванной связи не
 * должен отвечать отказом.
 */
@Service
@RequiredArgsConstructor
public class LeaveRoomTeamUseCase {

    private final RoomAccessGuard roomAuthz;
    private final SaverRoom saverRoom;
    private final RoomSeats roomSeats;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void run(HotHatUser user, String roomId, String teamId) {
        Room room = roomAuthz.requireRoomForWrite(roomId);
        RoomPlayer player = roomAuthz.requireMember(user, roomId);
        if (!teamId.equals(player.getTeamId())) {
            return;
        }
        RoomSeatingPolicy.refuseTeamLeave(RoomPhase.fromWire(room.getPhase()), false)
                .ifPresent(refusal -> {
                    throw RoomRefusals.of(refusal);
                });

        roomSeats.removeFromTeams(roomId, user.uid());
        player.setTeamId(null);
        player.setLastSeenAt(System.currentTimeMillis());
        saverRoom.savePlayer(player);
    }
}
