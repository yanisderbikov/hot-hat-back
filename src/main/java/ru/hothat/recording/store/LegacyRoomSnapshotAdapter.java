package ru.hothat.recording.store;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.recording.port.RoomSnapshotPort;
import ru.hothat.repository.GetterRoom;
import ru.hothat.util.Divisions;
import ru.hothat.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Снимок комнаты для паспорта записи.
 *
 * <p>Комната ещё не переехала в свои таблицы v2, поэтому снимок собирается из
 * сегодняшних. Область записей об этом не знает: она видит
 * {@link RoomSnapshotPort} и ничего больше, а когда комната переедет,
 * поменяется только этот файл.
 *
 * <p>Флаг «писать партии» тоже приезжает отсюда, а не из
 * {@code room_recording_policy}: ставит его хозяин комнаты через адрес области
 * комнаты, и пока писатель там, читателю нечего делать в чужой таблице.
 */
@Component
@RequiredArgsConstructor
public class LegacyRoomSnapshotAdapter implements RoomSnapshotPort {

    private final GetterRoom getterRoom;

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Optional<RoomSnapshot> find(String roomId) {
        return getterRoom.getById(Json.str(roomId).trim()).map(this::snapshot);
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public boolean isMember(String roomId, String uid) {
        return uid != null && !uid.isBlank() && getterRoom.getPlayer(roomId, uid).isPresent();
    }

    private RoomSnapshot snapshot(Room room) {
        List<Seat> seats = new ArrayList<>();
        for (RoomPlayer player : getterRoom.getPlayers(room.getId())) {
            seats.add(new Seat(player.getUid(),
                    player.getName() == null ? "Игрок" : player.getName(),
                    player.getTeamId(),
                    Boolean.TRUE.equals(player.getIsTestBot())));
        }
        List<Team> teams = new ArrayList<>();
        for (RoomTeam team : getterRoom.getTeams(room.getId())) {
            teams.add(new Team(team.getTeamId(),
                    team.getName() == null ? "Команда" : team.getName(),
                    team.getRankedTeamId(),
                    team.getScore() == null ? 0 : team.getScore(),
                    team.getMemberUids() == null ? List.of() : List.copyOf(team.getMemberUids())));
        }
        return new RoomSnapshot(
                room.getId(),
                room.getName(),
                room.getPhase() == null ? "setup" : room.getPhase(),
                room.getGameNumber() == null ? 0 : Math.max(0, room.getGameNumber()),
                Boolean.TRUE.equals(room.getRecordGame()),
                room.getGameMode(),
                Boolean.TRUE.equals(room.getRanked()),
                Boolean.TRUE.equals(room.getIsPrivate()),
                Boolean.TRUE.equals(room.getIsTestRoom()),
                Divisions.normalize(room.getDivisionLanguage()),
                Divisions.normalize(room.getGameLanguage() == null
                        ? room.getDivisionLanguage() : room.getGameLanguage()),
                room.getWordCount() == null ? 0 : room.getWordCount(),
                seats,
                teams);
    }
}
