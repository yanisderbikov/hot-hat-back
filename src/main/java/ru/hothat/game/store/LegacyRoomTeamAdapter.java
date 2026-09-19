package ru.hothat.game.store;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.game.port.RoomTeamPort;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Переходник к командам комнаты.
 *
 * <p>Единственная реализация {@link RoomTeamPort}: у области комнаты своей
 * пока нет. Соседний переходник к жизненному циклу уже удалён — его заменила
 * {@code room.spi.RoomLifecycleProvider}; этот уйдёт тем же путём и в тот же
 * день, когда команды комнаты переедут в {@code v2.room_team}.
 */
@Component
@RequiredArgsConstructor
public class LegacyRoomTeamAdapter implements RoomTeamPort {

    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;
    private final MatchTeamScoreRepo scoreRepo;

    @Override
    public List<RoomTeamView> teams(String roomId) {
        return getterRoom.getTeams(roomId).stream()
                .map(team -> new RoomTeamView(team.getTeamId(), team.getName(),
                        team.getOrder() == null ? 0 : team.getOrder(),
                        team.getScore() == null ? 0 : team.getScore(),
                        team.getMemberUids() == null ? List.of() : List.copyOf(team.getMemberUids())))
                .toList();
    }

    @Override
    public void addScore(String roomId, String teamId, int delta) {
        scoreRepo.addScore(roomId, teamId, delta, Instant.now());
    }

    @Override
    public void freezeRoster(String roomId, String teamId, List<String> memberUids) {
        RoomTeam team = getterRoom.getTeam(roomId, teamId).orElse(null);
        if (team == null) {
            return;
        }
        team.setMemberUids(new ArrayList<>(memberUids));
        team.setScore(0);
        team.setUpdatedAt(Instant.now());
        saverRoom.saveTeam(team);
    }
}
