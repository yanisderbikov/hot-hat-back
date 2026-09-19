package ru.hothat.game.store;

import ru.hothat.config.ApiException;
import ru.hothat.game.domain.MatchPlayer;
import ru.hothat.game.domain.MatchState;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Открытая партия: состояние движка и всё, что нужно записать обратно.
 *
 * <p>Сценарий работает только с {@link MatchState} и {@link MatchPlayer} —
 * строк базы он не видит. Здесь они лежат рядом, чтобы в конце сценария
 * записать ровно тронутое: партию и тех игроков, которых сценарий спросил.
 *
 * <p>Объект живёт внутри одной транзакции одного сценария и наружу области
 * не выходит.
 */
public final class MatchSession {

    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;
    private final MatchTeamScoreRepo teamScoreRepo;

    private final Room room;
    private final MatchState state;
    private final Map<String, RoomPlayer> rows = new LinkedHashMap<>();
    private final Map<String, MatchPlayer> players = new LinkedHashMap<>();
    private final List<Runnable> deferredScores = new ArrayList<>();
    private boolean playersLoaded;

    MatchSession(GetterRoom getterRoom, SaverRoom saverRoom, MatchTeamScoreRepo teamScoreRepo, Room room) {
        this.getterRoom = getterRoom;
        this.saverRoom = saverRoom;
        this.teamScoreRepo = teamScoreRepo;
        this.room = room;
        this.state = MatchMapper.toState(room);
    }

    public MatchState state() {
        return state;
    }

    public String roomId() {
        return room.getId();
    }

    /** Партия пишется в видеофайл: остановить его — забота сценария. */
    public boolean recorded() {
        return Boolean.TRUE.equals(room.getRecordGame());
    }

    public String hostUid() {
        return room.getCreatedBy();
    }

    /**
     * Игрок партии. Нет строки — 403, а не 404: спрашивающий не должен
     * узнавать по коду ответа, есть ли в комнате такой человек.
     */
    public MatchPlayer player(String uid) {
        return findPlayer(uid).orElseThrow(() -> ApiException.of("PLAYER_NOT_FOUND", 403));
    }

    public java.util.Optional<MatchPlayer> findPlayer(String uid) {
        MatchPlayer loaded = players.get(uid);
        if (loaded != null) {
            return java.util.Optional.of(loaded);
        }
        return getterRoom.getPlayer(room.getId(), uid).map(this::attach);
    }

    /** Все игроки комнаты одним чтением: по одному в цикле читать запрещено. */
    public List<MatchPlayer> players() {
        if (!playersLoaded) {
            getterRoom.getPlayers(room.getId()).forEach(this::attach);
            playersLoaded = true;
        }
        return List.copyOf(players.values());
    }

    private MatchPlayer attach(RoomPlayer row) {
        MatchPlayer existing = players.get(row.getUid());
        if (existing != null) {
            return existing;
        }
        MatchPlayer player = MatchMapper.toPlayer(row, room);
        rows.put(row.getUid(), row);
        players.put(row.getUid(), player);
        return player;
    }

    /** Прибавка к счёту команды; применяется при записи сессии. */
    public void addTeamScore(String teamId, int delta) {
        if (teamId == null || delta == 0) {
            return;
        }
        deferredScores.add(() -> teamScoreRepo.addScore(room.getId(), teamId, delta, java.time.Instant.now()));
    }

    /** Записать партию и тронутых игроков. */
    public void commit() {
        MatchMapper.apply(state, room);
        saverRoom.save(room);
        List<RoomPlayer> touched = new ArrayList<>();
        players.forEach((uid, player) -> {
            RoomPlayer row = rows.get(uid);
            MatchMapper.apply(player, row);
            touched.add(row);
        });
        if (!touched.isEmpty()) {
            saverRoom.savePlayers(touched);
        }
        deferredScores.forEach(Runnable::run);
        deferredScores.clear();
    }
}
