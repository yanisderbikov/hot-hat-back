package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.admin.api.dto.LiveActivitySummaryView;
import ru.hothat.admin.api.dto.LiveRoomDashboardResponseDTO;
import ru.hothat.admin.api.dto.LiveRoomPlayerView;
import ru.hothat.admin.api.dto.LiveRoomQueryDTO;
import ru.hothat.admin.api.dto.LiveRoomView;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.repository.GetterRoom;
import ru.hothat.auth.spi.AccountPort;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Показать администратору открытые комнаты и текущую нагрузку.
 *
 * <p>Заменяет {@code GET /api/admin?scope=rooms}. Слово {@code all} исчезло:
 * оно склеивало этот ответ со статистикой за период, и клиенту всё равно
 * приходилось делать второй запрос за расходом ресурсов
 * ({@code admin.js:271}). Два предмета — два адреса.
 *
 * <p>Чтений ровно четыре, и ни одного в цикле по коллекции: комнаты, игроки
 * всех комнат разом ({@code getPlayersOfRooms}), учётки всех игроков разом
 * ({@link AdminAccountDirectory}) и счётчик учёток. Раньше на каждого игрока
 * уходило отдельное обращение к базе.
 */
@Service
@RequiredArgsConstructor
public class GetLiveRoomDashboardUseCase {

    /** Игрок считается активным, если писал heartbeat за последние четыре минуты. */
    private static final long ACTIVE_WINDOW_MS = 4 * 60 * 1000;

    /** Так движок подписывает участника, у которого нет имени. */
    private static final String PLACEHOLDER_NAME = "Игрок";

    private final GetterRoom getterRoom;
    /** Сколько всего учёток — знает область личности, а не оболочка. */
    private final AccountPort accounts;
    private final AdminAccountDirectory accountDirectory;

    /**
     * С инициализатором, поэтому в конструктор Lombok его не берёт: отдельного
     * бина {@code Clock} в приложении нет, а заводить общий ради одной области
     * значило бы навязать его всем.
     */
    private final Clock clock = Clock.systemUTC();

    @PreAuthorize("hasRole('ADMIN')")
    public LiveRoomDashboardResponseDTO run(HotHatUser admin, LiveRoomQueryDTO query) {
        // Единственное место умолчания: параметров может не быть вовсе.
        int limit = query == null ? LiveRoomQueryDTO.DEFAULT_LIMIT : query.limitOrDefault();
        long now = clock.millis();

        List<Room> openRooms = getterRoom.getAll(limit).stream()
                .filter(room -> !room.isClosed())
                .toList();
        Map<String, List<RoomPlayer>> playersByRoom = playersByRoom(openRooms);
        Set<String> uids = new LinkedHashSet<>();
        playersByRoom.values().forEach(list -> list.forEach(player -> uids.add(player.getUid())));
        AdminAccountDirectory.Snapshot snapshot = accountDirectory.load(uids);

        List<LiveRoomView> rooms = new ArrayList<>();
        Set<String> activeUids = new LinkedHashSet<>();
        int activeRooms = 0;
        for (Room room : openRooms) {
            List<LiveRoomPlayerView> players = new ArrayList<>();
            int active = 0;
            for (RoomPlayer player : playersByRoom.getOrDefault(room.getId(), List.of())) {
                boolean testBot = Boolean.TRUE.equals(player.getIsTestBot());
                long lastSeenAt = player.getLastSeenAt() == null ? 0L : player.getLastSeenAt();
                boolean alive = testBot || lastSeenAt >= now - ACTIVE_WINDOW_MS;
                if (alive) {
                    active++;
                    activeUids.add(player.getUid());
                }
                players.add(new LiveRoomPlayerView(
                        player.getUid(),
                        player.getName() == null || player.getName().isBlank()
                                ? PLACEHOLDER_NAME : player.getName(),
                        player.getTeamId(),
                        alive,
                        testBot,
                        lastSeenAt == 0 ? null : lastSeenAt,
                        snapshot.email(player.getUid()),
                        snapshot.banned(player.getUid()),
                        snapshot.registered(player.getUid())));
            }
            if (active > 0) {
                activeRooms++;
            }
            rooms.add(new LiveRoomView(
                    room.getId(),
                    room.getName() == null || room.getName().isBlank() ? room.getId() : room.getName(),
                    room.getPhase(),
                    room.getCreatedAt() == null ? 0L : room.getCreatedAt().toEpochMilli(),
                    room.getUpdatedAt() == null ? 0L : room.getUpdatedAt().toEpochMilli(),
                    room.getGameNumber() == null ? 0 : room.getGameNumber(),
                    room.getWordCount() == null ? 0 : room.getWordCount(),
                    room.getTeamOrder() == null ? 0 : room.getTeamOrder().size(),
                    active,
                    players));
        }
        rooms.sort(Comparator.comparingLong(LiveRoomView::updatedAtMs).reversed());

        LiveActivitySummaryView activity = new LiveActivitySummaryView(
                activeUids.size(), activeRooms, rooms.size(), accounts.countAccounts());
        return new LiveRoomDashboardResponseDTO(rooms, null, limit, activity);
    }

    /** Игроки всех открытых комнат — одним запросом, а не по комнате за раз. */
    private Map<String, List<RoomPlayer>> playersByRoom(List<Room> rooms) {
        Map<String, List<RoomPlayer>> byRoom = new LinkedHashMap<>();
        if (rooms.isEmpty()) {
            return byRoom;
        }
        List<String> roomIds = rooms.stream().map(Room::getId).toList();
        for (RoomPlayer player : getterRoom.getPlayersOfRooms(roomIds)) {
            byRoom.computeIfAbsent(player.getRoomId(), key -> new ArrayList<>()).add(player);
        }
        return byRoom;
    }
}
