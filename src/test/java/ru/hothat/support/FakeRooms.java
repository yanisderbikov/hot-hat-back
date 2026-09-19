package ru.hothat.support;

import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomSpectator;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.model.room.RoomWordSubmission;
import ru.hothat.repository.GetterRoom;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Комнаты в памяти: строка комнаты, места игроков и места зрителей.
 *
 * <p>Правила доступа читают ровно эти три вещи, поэтому остальные методы
 * отдают пустые списки, а не подделку: подделанное чтение выглядело бы
 * проверенным, не будучи им.
 */
public final class FakeRooms implements GetterRoom {

    private final List<Room> rooms = new ArrayList<>();
    private final List<RoomPlayer> players = new ArrayList<>();
    private final List<RoomSpectator> spectators = new ArrayList<>();

    /** Сколько раз спрашивали строку комнаты: по этому счёту видно кеш и лишние чтения. */
    private int roomReads;

    public static FakeRooms empty() {
        return new FakeRooms();
    }

    /** Комната с названным хозяином: хозяйство хранится колонкой createdBy. */
    public FakeRooms room(String roomId, String hostUid) {
        rooms.add(Room.builder().id(roomId).name("Шляпа").phase("setup").createdBy(hostUid).build());
        return this;
    }

    public FakeRooms player(String roomId, String uid, String name) {
        players.add(RoomPlayer.builder().roomId(roomId).uid(uid).name(name).build());
        return this;
    }

    public FakeRooms spectator(String roomId, String uid, String name) {
        spectators.add(RoomSpectator.builder().roomId(roomId).uid(uid).name(name).build());
        return this;
    }

    public int roomReads() {
        return roomReads;
    }

    @Override
    public Optional<Room> getById(String roomId) {
        roomReads++;
        return rooms.stream().filter(room -> room.getId().equals(roomId)).findFirst();
    }

    @Override
    public List<Room> getByIds(List<String> roomIds) {
        return rooms.stream().filter(room -> roomIds.contains(room.getId())).toList();
    }

    @Override
    public List<Room> getAll(int limit) {
        return List.copyOf(rooms);
    }

    @Override
    public List<Room> getByPhase(String phase, int limit) {
        return rooms.stream().filter(room -> phase.equals(room.getPhase())).toList();
    }

    @Override
    public List<RoomPlayer> getPlayers(String roomId) {
        return players.stream().filter(row -> row.getRoomId().equals(roomId)).toList();
    }

    @Override
    public List<RoomPlayer> getPlayersOfRooms(List<String> roomIds) {
        return players.stream().filter(row -> roomIds.contains(row.getRoomId())).toList();
    }

    @Override
    public Optional<RoomPlayer> getPlayer(String roomId, String uid) {
        return players.stream()
                .filter(row -> row.getRoomId().equals(roomId) && row.getUid().equals(uid))
                .findFirst();
    }

    @Override
    public List<RoomPlayer> getPlayerRowsOfUid(String uid) {
        return players.stream().filter(row -> row.getUid().equals(uid)).toList();
    }

    @Override
    public List<RoomSpectator> getSpectators(String roomId) {
        return spectators.stream().filter(row -> row.getRoomId().equals(roomId)).toList();
    }

    @Override
    public Optional<RoomSpectator> getSpectator(String roomId, String uid) {
        return spectators.stream()
                .filter(row -> row.getRoomId().equals(roomId) && row.getUid().equals(uid))
                .findFirst();
    }

    @Override
    public List<RoomSpectator> getSpectatorRowsOfUid(String uid) {
        return spectators.stream().filter(row -> row.getUid().equals(uid)).toList();
    }

    @Override
    public List<RoomTeam> getTeams(String roomId) {
        return List.of();
    }

    @Override
    public Optional<RoomTeam> getTeam(String roomId, String teamId) {
        return Optional.empty();
    }

    @Override
    public List<RoomWordSubmission> getWordSubmissions(String roomId) {
        return List.of();
    }
}
