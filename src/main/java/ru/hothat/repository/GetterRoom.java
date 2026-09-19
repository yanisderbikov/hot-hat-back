package ru.hothat.repository;

import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomSpectator;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.model.room.RoomWordSubmission;

import java.util.List;
import java.util.Optional;

public interface GetterRoom {

    Optional<Room> getById(String roomId);

    /**
     * Комнаты по списку идентификаторов — одним запросом с «id in (…)».
     *
     * <p>Существует ради того, чтобы не звать {@link #getById(String)} в цикле:
     * смена ника переписывает копию имени в каждой комнате, где игрок числится,
     * и до этого метода каждая комната стоила отдельного обращения. Порядок
     * результата не определён — вызывающий раскладывает его в карту по id.
     *
     * <p>Пустой список в базу не идёт.
     */
    List<Room> getByIds(List<String> roomIds);

    List<Room> getAll(int limit);

    List<Room> getByPhase(String phase, int limit);

    /**
     * Места комнаты в порядке посадки ({@code joinedAt}, при равенстве —
     * {@code uid}). Порядок — часть договора: экран рисует плитки в том
     * порядке, в каком строки пришли в кадре, и кадр за кадром он не должен
     * меняться от чужого heartbeat'а; это тот же порядок, что давала прежняя
     * подписка {@code players orderBy joinedAt}.
     */
    List<RoomPlayer> getPlayers(String roomId);

    List<RoomPlayer> getPlayersOfRooms(List<String> roomIds);

    Optional<RoomPlayer> getPlayer(String roomId, String uid);

    List<RoomPlayer> getPlayerRowsOfUid(String uid);

    /** Зрители в порядке посадки — по тому же договору, что {@link #getPlayers}. */
    List<RoomSpectator> getSpectators(String roomId);

    Optional<RoomSpectator> getSpectator(String roomId, String uid);

    List<RoomSpectator> getSpectatorRowsOfUid(String uid);

    /** Команды отсортированы по order, затем по id — как в prepare_game. */
    List<RoomTeam> getTeams(String roomId);

    Optional<RoomTeam> getTeam(String roomId, String teamId);

    List<RoomWordSubmission> getWordSubmissions(String roomId);
}
