package ru.hothat.room.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomSpectator;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.util.Json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Реализация {@link RoomNicknamePort}: пишет владелец таблиц комнаты.
 *
 * <p>Комнаты читаются пачкой, а не по одной внутри цикла по местам. Разница
 * не косметическая: игрок, сидящий в комнате и зрителем в другой, стоил
 * четырёх чтений одной и той же строки — по одному на каждое место и ещё по
 * одному на каждую подпись хода.
 */
@Component
@RequiredArgsConstructor
public class RoomNicknames implements RoomNicknamePort {

    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;

    @Override
    public int renameEverywhere(String uid, String nickname) {
        long now = System.currentTimeMillis();
        List<RoomPlayer> players = getterRoom.getPlayerRowsOfUid(uid);
        List<RoomSpectator> spectators = getterRoom.getSpectatorRowsOfUid(uid);
        Set<String> roomIds = new LinkedHashSet<>();
        for (RoomPlayer player : players) {
            player.setName(nickname);
            if (player.getLastSeenAt() == null || player.getLastSeenAt() == 0) {
                player.setLastSeenAt(now);
            }
            saverRoom.savePlayer(player);
            roomIds.add(player.getRoomId());
        }
        for (RoomSpectator spectator : spectators) {
            spectator.setName(nickname);
            if (spectator.getLastSeenAt() == null || spectator.getLastSeenAt() == 0) {
                spectator.setLastSeenAt(now);
            }
            saverRoom.saveSpectator(spectator);
            roomIds.add(spectator.getRoomId());
        }
        if (roomIds.isEmpty()) {
            return 0;
        }
        int touched = 0;
        for (Room room : getterRoom.getByIds(new ArrayList<>(roomIds))) {
            if (applyToRoom(room, uid, nickname)) {
                touched++;
            }
        }
        return touched;
    }

    @Override
    public boolean renameIn(String roomId, String uid, String nickname) {
        Room room = getterRoom.getById(roomId).orElse(null);
        if (room == null) {
            return false;
        }
        boolean touched = false;
        Optional<RoomPlayer> player = getterRoom.getPlayer(roomId, uid);
        if (player.isPresent()) {
            player.get().setName(nickname);
            saverRoom.savePlayer(player.get());
            touched = true;
        }
        Optional<RoomSpectator> spectator = getterRoom.getSpectator(roomId, uid);
        if (spectator.isPresent()) {
            spectator.get().setName(nickname);
            saverRoom.saveSpectator(spectator.get());
            touched = true;
        }
        return applyToRoom(room, uid, nickname) || touched;
    }

    /** Подписи внутри самой комнаты: карта имён партии и две роли хода. */
    private boolean applyToRoom(Room room, String uid, String nickname) {
        boolean touched = false;
        Map<String, Object> names = new HashMap<>(Json.map(room.getGamePlayerNamesByUid()));
        if (names.containsKey(uid)) {
            names.put(uid, nickname);
            room.setGamePlayerNamesByUid(names);
            touched = true;
        }
        if (uid.equals(room.getExplainerUid()) && !nickname.equals(room.getExplainerName())) {
            room.setExplainerName(nickname);
            touched = true;
        }
        if (uid.equals(room.getGuesserUid()) && !nickname.equals(room.getGuesserName())) {
            room.setGuesserName(nickname);
            touched = true;
        }
        if (touched) {
            saverRoom.save(room);
        }
        return touched;
    }
}
