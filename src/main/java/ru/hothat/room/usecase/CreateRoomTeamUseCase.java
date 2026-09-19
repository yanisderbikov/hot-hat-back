package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.CreateRoomTeamRequestDTO;
import ru.hothat.room.api.dto.RoomTeamResponseDTO;
import ru.hothat.room.domain.RoomPhase;
import ru.hothat.room.domain.RoomSeatingPolicy;
import ru.hothat.util.Ids;
import ru.hothat.util.Json;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Завести команду в комнате.
 *
 * <p>Переезд {@code addTeam()} ({@code app-core.js:12684}). Там это была
 * клиентская транзакция, которая читала документ комнаты, проверяла предел в
 * пять команд и приписывала идентификатор в конец массива {@code teamOrder}.
 * Уникальность названия при этом проверялась не в транзакции, а по списку,
 * который лежал на экране, — то есть двое, нажавшие одновременно, заводили две
 * «Соколы».
 *
 * <p>Команду заводит любой участник, не только хозяин: собирать составы в
 * комнате незнакомых людей — общее дело, и до сих пор так и было.
 *
 * <p>Очередь ходов и набор строк команд пишутся под одним замком комнаты.
 * Разойтись им нельзя: партия берёт очередь из {@code teamOrder}, а составы —
 * из строк, и лишний идентификатор в очереди означал бы ход команды, которой
 * нет.
 */
@Service
@RequiredArgsConstructor
public class CreateRoomTeamUseCase {

    private final RoomAccessGuard roomAuthz;
    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;
    private final RoomProjections projections;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public RoomTeamResponseDTO run(HotHatUser user, String roomId, CreateRoomTeamRequestDTO request) {
        Room room = roomAuthz.requireRoomForWrite(roomId);
        roomAuthz.requireMember(user, roomId);

        List<RoomTeam> teams = getterRoom.getTeams(roomId);
        String name = Json.str(request.name().replaceAll("\\s+", " ").trim(), 50);
        RoomSeatingPolicy.refuseTeamCreation(
                RoomPhase.fromWire(room.getPhase()),
                teams.size(),
                name,
                teams.stream().map(RoomTeam::getName).toList())
                .ifPresent(refusal -> {
                    throw RoomRefusals.of(refusal);
                });

        RoomTeam team = RoomTeam.builder()
                .roomId(roomId)
                .teamId("team-" + Ids.hex(8))
                .name(name)
                .order(teams.size())
                .score(0)
                .memberUids(new ArrayList<>())
                .createdAt(Instant.now())
                .build();
        saverRoom.saveTeam(team);

        List<String> order = new ArrayList<>(
                room.getTeamOrder() == null ? List.<String>of() : room.getTeamOrder());
        order.add(team.getTeamId());
        room.setTeamOrder(order);
        saverRoom.save(room);

        return new RoomTeamResponseDTO(projections.team(team), List.copyOf(order));
    }
}
