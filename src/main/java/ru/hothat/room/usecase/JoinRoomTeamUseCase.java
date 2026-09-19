package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.RoomTeamMembershipResponseDTO;
import ru.hothat.room.api.dto.RoomTeamView;
import ru.hothat.room.domain.RoomPhase;
import ru.hothat.room.domain.RoomSeatingPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Сесть в команду.
 *
 * <p>Переезд {@code joinTeam()} ({@code app-core.js:12511}) — самой дорогой из
 * клиентских транзакций. Она читала комнату, своё место, целевую команду, а
 * затем строку каждого, кто в этой команде уже числился, чтобы узнать, жив ли
 * он: до четырёх чтений на нажатие кнопки. И всё равно оставалась уговором —
 * состав команды писался прямой записью документа, а путь {@code rooms/*} в
 * проверке прав пуст (находка A1).
 *
 * <p>Пересадка — это две записи: старый состав теряет игрока, новый получает.
 * Под одним замком комнаты они не разъезжаются; раньше между ними умещалась
 * чужая транзакция, и человек оказывался сразу в двух командах.
 *
 * <p>Повторное нажатие на свою же команду ничего не меняет и не отвечает
 * «команда полна»: считать себя чужим при подсчёте мест — прямой путь к тому,
 * чтобы вытеснить себя самого.
 */
@Service
@RequiredArgsConstructor
public class JoinRoomTeamUseCase {

    private final RoomAccessGuard roomAuthz;
    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;
    private final RoomSeats roomSeats;
    private final RoomProjections projections;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public RoomTeamMembershipResponseDTO run(HotHatUser user, String roomId, String teamId) {
        Room room = roomAuthz.requireRoomForWrite(roomId);
        RoomPlayer player = roomAuthz.requireMember(user, roomId);
        RoomTeam target = getterRoom.getTeam(roomId, teamId)
                .orElseThrow(() -> ApiException.of("ROOM_TEAM_NOT_FOUND", 404));

        long now = System.currentTimeMillis();
        boolean alreadyInTeam = teamId.equals(player.getTeamId());
        int aliveMembers = (int) roomSeats.alivePlayers(roomId, now).stream()
                .filter(seated -> teamId.equals(seated.getTeamId()))
                .filter(seated -> !seated.getUid().equals(user.uid()))
                .count();
        RoomSeatingPolicy.refuseTeamJoin(RoomPhase.fromWire(room.getPhase()), aliveMembers, alreadyInTeam)
                .ifPresent(refusal -> {
                    throw RoomRefusals.of(refusal);
                });

        // Снятие идёт до посадки: иначе игрок на мгновение числился бы
        // в обеих командах, и параллельное чтение состава увидело бы тройку.
        Optional<RoomTeam> previousTeam = roomSeats.removeFromTeams(roomId, user.uid());
        List<String> members = new ArrayList<>(
                target.getMemberUids() == null ? List.<String>of() : target.getMemberUids());
        if (!members.contains(user.uid())) {
            members.add(user.uid());
        }
        target.setMemberUids(members);
        saverRoom.saveTeam(target);

        player.setTeamId(teamId);
        player.setLastSeenAt(now);
        saverRoom.savePlayer(player);

        // Команда, из которой вышли, называется только если она другая:
        // повторное нажатие на свою же не должно выглядеть пересадкой.
        RoomTeamView previousView = previousTeam
                .filter(team -> !team.getTeamId().equals(teamId))
                .map(projections::team)
                .orElse(null);
        return new RoomTeamMembershipResponseDTO(
                projections.team(target), previousView, projections.seat(player, now));
    }
}
