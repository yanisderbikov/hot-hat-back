package ru.hothat.repository.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomChatMessage;
import ru.hothat.model.room.RoomMemberId;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomSpectator;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.model.room.RoomTeamId;
import ru.hothat.model.room.RoomWordSubmission;
import ru.hothat.model.room.RoomWordSubmissionId;
import ru.hothat.repository.GetterRoom;
import ru.hothat.realtime.spi.RealtimeChangeBus;
import ru.hothat.repository.SaverRoom;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@AllArgsConstructor
@Slf4j
class RoomManager implements GetterRoom, SaverRoom {

    private final RoomRepo roomRepo;
    private final RoomPlayerRepo roomPlayerRepo;
    private final RoomSpectatorRepo roomSpectatorRepo;
    private final RoomTeamRepo roomTeamRepo;
    private final RoomChatMessageRepo roomChatMessageRepo;
    private final RoomWordSubmissionRepo roomWordSubmissionRepo;

    /**
     * Каналы {@code /ws/v2/room} и {@code /ws/v2/lobby} узнают об изменении
     * комнаты только из событий шины, а публиковать их
     * обязан тот, кто пишет. Писателей у комнаты — 35 сценариев {@code /api/v2},
     * и все они проходят через этот класс; одна публикация здесь надёжнее,
     * чем 35 вызовов по одному в каждом, и ни один новый сценарий её не забудет.
     *
     * <p>Событие уходит внутри транзакции писателя, а слушатели каналов
     * помечены {@code AFTER_COMMIT}: они увидят уже зафиксированную строку.
     * Шина склеивает повторы в одной транзакции, поэтому сценарий с пятью
     * записями рассылает один кадр.
     */
    private final RealtimeChangeBus changes;

    @Override
    public Optional<Room> getById(String roomId) {
        return wrap("getById", () -> roomRepo.findById(roomId));
    }

    @Override
    public List<Room> getByIds(List<String> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return List.of();
        }
        // findAllById — один запрос с «id in (…)»; поштучный getById в цикле
        // и есть тот веер, ради которого метод появился.
        return wrap("getByIds", () -> roomRepo.findAllById(roomIds));
    }

    @Override
    public List<Room> getAll(int limit) {
        return wrap("getAll", () -> roomRepo.findAllBy(Limit.of(limit)));
    }

    @Override
    public List<Room> getByPhase(String phase, int limit) {
        return wrap("getByPhase", () -> roomRepo.findByPhase(phase, Limit.of(limit)));
    }

    @Override
    public List<RoomPlayer> getPlayers(String roomId) {
        // Порядок посадки, а не порядок кортежей: экран рисует места в том
        // порядке, в каком они пришли, и после чужого heartbeat'а плитки не
        // должны меняться местами.
        return wrap("getPlayers", () -> roomPlayerRepo.findByRoomIdOrderByJoinedAtAscUidAsc(roomId));
    }

    @Override
    public List<RoomPlayer> getPlayersOfRooms(List<String> roomIds) {
        if (roomIds.isEmpty()) {
            return List.of();
        }
        return wrap("getPlayersOfRooms", () -> roomPlayerRepo.findByRoomIdIn(roomIds));
    }

    @Override
    public Optional<RoomPlayer> getPlayer(String roomId, String uid) {
        return wrap("getPlayer", () -> roomPlayerRepo.findById(new RoomMemberId(roomId, uid)));
    }

    @Override
    public List<RoomPlayer> getPlayerRowsOfUid(String uid) {
        return wrap("getPlayerRowsOfUid", () -> roomPlayerRepo.findByUid(uid));
    }

    @Override
    public List<RoomSpectator> getSpectators(String roomId) {
        return wrap("getSpectators", () -> roomSpectatorRepo.findByRoomIdOrderByJoinedAtAscUidAsc(roomId));
    }

    @Override
    public Optional<RoomSpectator> getSpectator(String roomId, String uid) {
        return wrap("getSpectator", () -> roomSpectatorRepo.findById(new RoomMemberId(roomId, uid)));
    }

    @Override
    public List<RoomSpectator> getSpectatorRowsOfUid(String uid) {
        return wrap("getSpectatorRowsOfUid", () -> roomSpectatorRepo.findByUid(uid));
    }

    @Override
    public List<RoomTeam> getTeams(String roomId) {
        return wrap("getTeams", () -> roomTeamRepo.findByRoomIdOrderByTeamOrderAscTeamIdAsc(roomId));
    }

    @Override
    public Optional<RoomTeam> getTeam(String roomId, String teamId) {
        return wrap("getTeam", () -> roomTeamRepo.findById(new RoomTeamId(roomId, teamId)));
    }

    @Override
    public List<RoomWordSubmission> getWordSubmissions(String roomId) {
        return wrap("getWordSubmissions", () -> roomWordSubmissionRepo.findByRoomId(roomId));
    }

    @Override
    public Room save(Room room) {
        room.setUpdatedAt(Instant.now());
        Room saved = wrap("save", () -> roomRepo.save(room));
        changes.roomRowChanged(saved.getId());
        return saved;
    }

    @Override
    public RoomPlayer savePlayer(RoomPlayer player) {
        player.setUpdatedAt(Instant.now());
        RoomPlayer saved = wrap("savePlayer", () -> roomPlayerRepo.save(player));
        changes.roomChanged(saved.getRoomId());
        return saved;
    }

    @Override
    public List<RoomPlayer> savePlayers(List<RoomPlayer> players) {
        players.forEach(p -> p.setUpdatedAt(Instant.now()));
        List<RoomPlayer> saved = wrap("savePlayers", () -> roomPlayerRepo.saveAll(players));
        saved.stream().map(RoomPlayer::getRoomId).distinct().forEach(changes::roomChanged);
        return saved;
    }

    @Override
    public void deletePlayer(String roomId, String uid) {
        wrap("deletePlayer", () -> {
            roomPlayerRepo.deleteById(new RoomMemberId(roomId, uid));
            return null;
        });
        changes.roomChanged(roomId);
    }

    @Override
    public RoomSpectator saveSpectator(RoomSpectator spectator) {
        spectator.setUpdatedAt(Instant.now());
        RoomSpectator saved = wrap("saveSpectator", () -> roomSpectatorRepo.save(spectator));
        changes.roomChanged(saved.getRoomId());
        return saved;
    }

    @Override
    public void deleteSpectator(String roomId, String uid) {
        wrap("deleteSpectator", () -> {
            roomSpectatorRepo.deleteById(new RoomMemberId(roomId, uid));
            return null;
        });
        changes.roomChanged(roomId);
    }

    @Override
    public RoomTeam saveTeam(RoomTeam team) {
        team.setUpdatedAt(Instant.now());
        RoomTeam saved = wrap("saveTeam", () -> roomTeamRepo.save(team));
        changes.roomChanged(saved.getRoomId());
        return saved;
    }

    @Override
    public List<RoomTeam> saveTeams(List<RoomTeam> teams) {
        teams.forEach(t -> t.setUpdatedAt(Instant.now()));
        List<RoomTeam> saved = wrap("saveTeams", () -> roomTeamRepo.saveAll(teams));
        saved.stream().map(RoomTeam::getRoomId).distinct().forEach(changes::roomChanged);
        return saved;
    }

    @Override
    public void deleteTeam(String roomId, String teamId) {
        wrap("deleteTeam", () -> {
            roomTeamRepo.deleteById(new RoomTeamId(roomId, teamId));
            return null;
        });
        changes.roomChanged(roomId);
    }

    @Override
    public RoomChatMessage saveChatMessage(RoomChatMessage message) {
        RoomChatMessage saved = wrap("saveChatMessage", () -> roomChatMessageRepo.save(message));
        changes.roomChanged(saved.getRoomId());
        return saved;
    }

    @Override
    public RoomWordSubmission saveWordSubmission(RoomWordSubmission submission) {
        submission.setUpdatedAt(Instant.now());
        RoomWordSubmission saved = wrap("saveWordSubmission", () -> roomWordSubmissionRepo.save(submission));
        changes.roomChanged(saved.getRoomId());
        return saved;
    }

    @Override
    public void deleteWordSubmission(String roomId, String submissionId) {
        wrap("deleteWordSubmission", () -> {
            roomWordSubmissionRepo.deleteById(new RoomWordSubmissionId(roomId, submissionId));
            return null;
        });
        changes.roomChanged(roomId);
    }

    /**
     * ON DELETE CASCADE снял бы подколлекции сам, но явное удаление держит
     * персистентный контекст в согласованном состоянии внутри той же транзакции.
     */
    @Override
    @Transactional
    public void deleteRoomTree(String roomId) {
        wrap("deleteRoomTree", () -> {
            roomChatMessageRepo.deleteByRoomId(roomId);
            roomWordSubmissionRepo.deleteByRoomId(roomId);
            roomSpectatorRepo.deleteByRoomId(roomId);
            roomPlayerRepo.deleteByRoomId(roomId);
            roomTeamRepo.deleteByRoomId(roomId);
            roomRepo.deleteById(roomId);
            return null;
        });
        // Комната исчезла: и её подписчикам, и витрине лобби.
        changes.roomRowChanged(roomId);
    }

    private <T> T wrap(String operation, java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (Exception e) {
            log.error("RoomManager.{} failed", operation, e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
