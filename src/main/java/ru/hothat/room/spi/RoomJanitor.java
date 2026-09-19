package ru.hothat.room.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.model.ops.MaintenanceState;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.repository.GetterOps;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverOps;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.domain.RoomCleanupPolicy;
import ru.hothat.room.domain.RoomPhase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Уборщик комнат: реализация {@link RoomJanitorPort}.
 *
 * <p>Переезд {@code RoomCleanupServiceImpl}. Решение «брошена ли» здесь не
 * принимается — оно в {@link RoomCleanupPolicy}, куда приезжают отметки
 * присутствия и фаза. Здесь остаётся чтение, удаление дерева комнаты и замок
 * от одновременных проходов.
 *
 * <p>Проход читает комнаты пачкой и не спрашивает игроков в цикле по каждой:
 * список мест берётся одним запросом на все прочитанные комнаты. Прежний
 * движок спрашивал по комнате за раз — тысяча комнат стоила тысячи запросов.
 */
@Service
@RequiredArgsConstructor
public class RoomJanitor implements RoomJanitorPort {

    /** Столько комнат читает один проход. */
    private static final int SWEEP_BATCH = 1000;

    /** Под этим ключом лежит отметка времени последнего прохода. */
    private static final String SWEEP_STATE_ID = "roomCleanup";

    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;
    private final GetterOps getterOps;
    private final SaverOps saverOps;

    @Override
    @Transactional
    public Cleanup cleanupOne(String roomId) {
        Room room = getterRoom.getById(roomId).orElse(null);
        if (room == null) {
            return new Cleanup(roomId, false, "missing");
        }
        RoomCleanupPolicy.Verdict verdict = verdict(room,
                humansByRoom(List.of(room.getId())).getOrDefault(room.getId(), List.of()),
                true, System.currentTimeMillis());
        if (!verdict.abandoned()) {
            return new Cleanup(roomId, false, verdict.reason());
        }
        saverRoom.deleteRoomTree(roomId);
        return new Cleanup(roomId, true, verdict.reason());
    }

    @Override
    @Transactional
    public Sweep sweep() {
        if (!claimSweep()) {
            return new Sweep(true, 0, List.of());
        }
        long now = System.currentTimeMillis();
        List<Room> rooms = getterRoom.getAll(SWEEP_BATCH);
        // Места всех прочитанных комнат — одним запросом. Прежний движок
        // спрашивал по комнате за раз: тысяча комнат стоила тысячи обращений.
        Map<String, List<Long>> humans = humansByRoom(rooms.stream().map(Room::getId).toList());
        List<Swept> swept = new ArrayList<>();
        for (Room room : rooms) {
            RoomCleanupPolicy.Verdict verdict = verdict(room,
                    humans.getOrDefault(room.getId(), List.of()), false, now);
            if (verdict.abandoned()) {
                saverRoom.deleteRoomTree(room.getId());
                swept.add(new Swept(room.getId(), verdict.reason()));
            }
        }
        return new Sweep(false, rooms.size(), swept);
    }

    /**
     * Признаки комнаты для политики.
     *
     * <p>Время создания берётся из {@code createdAt}, а при его отсутствии — из
     * {@code updatedAt}: у комнат старого поколения первой колонки нет, и без
     * запасной они получали бы фору всегда.
     */
    private static RoomCleanupPolicy.Verdict verdict(Room room, List<Long> humanLastSeen,
                                                     boolean targeted, long nowMs) {
        long createdAt = room.getCreatedAt() != null
                ? room.getCreatedAt().toEpochMilli()
                : (room.getUpdatedAt() == null ? 0L : room.getUpdatedAt().toEpochMilli());
        return RoomCleanupPolicy.verdict(
                new RoomCleanupPolicy.Occupancy(RoomPhase.fromWire(room.getPhase()), createdAt, humanLastSeen),
                targeted, nowMs);
    }

    /**
     * Отметки присутствия людей по комнатам — одним запросом на весь список.
     *
     * <p>Тест-боты отсеиваются здесь: они намеренно не шлют отметок, и
     * брошенный тестовый прогон подвешивал бы комнату навсегда.
     */
    private Map<String, List<Long>> humansByRoom(List<String> roomIds) {
        Map<String, List<Long>> byRoom = new LinkedHashMap<>();
        for (RoomPlayer player : getterRoom.getPlayersOfRooms(roomIds)) {
            if (Boolean.TRUE.equals(player.getIsTestBot())) {
                continue;
            }
            byRoom.computeIfAbsent(player.getRoomId(), key -> new ArrayList<>())
                    .add(player.getLastSeenAt() == null ? 0L : player.getLastSeenAt());
        }
        return byRoom;
    }

    /** Кулдаун защищает от одновременных проходов с нескольких вкладок. */
    private boolean claimSweep() {
        long now = System.currentTimeMillis();
        MaintenanceState state = getterOps.getMaintenance(SWEEP_STATE_ID)
                .orElseGet(() -> MaintenanceState.builder().id(SWEEP_STATE_ID).build());
        if (state.getLastRunAt() > 0 && now - state.getLastRunAt() < RoomCleanupPolicy.SWEEP_COOLDOWN_MS) {
            return false;
        }
        state.setLastRunAt(now);
        saverOps.saveMaintenance(state);
        return true;
    }
}
