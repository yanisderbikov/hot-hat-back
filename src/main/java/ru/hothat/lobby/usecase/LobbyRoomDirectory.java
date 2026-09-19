package ru.hothat.lobby.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.lobby.domain.LobbyVisibility;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.repository.GetterRoom;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сколько живых игроков в каждой комнате витрины — одним запросом на весь
 * список сразу.
 *
 * <p>Появился ради того же веера, что справочники дружбы и переписки. Браузер
 * сегодня узнаёт состав комнаты подпиской на её игроков, то есть заводит
 * отдельную подписку на каждую комнату, которую хочет показать; сервер,
 * повторив это в лоб, читал бы состав комнаты в цикле по списку. Здесь строки
 * игроков всех названных комнат забираются пачкой ({@code getPlayersOfRooms} —
 * «room_id in (…)»), а дальше счёт идёт по карте в памяти.
 *
 * <p>Спрашивают у него только те комнаты, чей собственный счётчик протух:
 * свежий счётчик комната ведёт сама, и за него платить чтением незачем.
 * В обычный час это означает ноль запросов на всю витрину.
 */
@Component
@RequiredArgsConstructor
public class LobbyRoomDirectory {

    private final GetterRoom getterRoom;

    /** Считает живых игроков; пустой список комнат в базу не идёт. */
    public Snapshot countAlivePlayers(Collection<String> roomIds, long nowMs) {
        List<String> wanted = roomIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        Map<String, Integer> byRoom = new HashMap<>();
        for (RoomPlayer player : getterRoom.getPlayersOfRooms(wanted)) {
            boolean alive = LobbyVisibility.playerAlive(
                    Boolean.TRUE.equals(player.getIsTestBot()),
                    player.getLastSeenAt() == null ? 0L : player.getLastSeenAt(),
                    nowMs);
            if (alive) {
                byRoom.merge(player.getRoomId(), 1, Integer::sum);
            }
        }
        return new Snapshot(byRoom);
    }

    /** Посчитанные комнаты. Запросов больше не делает — только карта в памяти. */
    public static final class Snapshot {

        private final Map<String, Integer> byRoom;

        private Snapshot(Map<String, Integer> byRoom) {
            this.byRoom = byRoom;
        }

        /** Сколько живых игроков в комнате; о комнате не спрашивали — ноль. */
        public int alive(String roomId) {
            return byRoom.getOrDefault(roomId, 0);
        }
    }
}
