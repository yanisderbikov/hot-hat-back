package ru.hothat.support;

import ru.hothat.model.room.Room;
import ru.hothat.room.store.RoomRows;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Замок на строке комнаты — в памяти.
 *
 * <p>Комнаты здесь заводятся отдельно от {@link FakeRooms} намеренно: только
 * так видно, идёт ли пишущий сценарий за строкой под замком или читает её
 * мимо замка обычным чтением.
 */
public final class FakeRoomRows implements RoomRows {

    private final List<Room> locked = new ArrayList<>();
    private int locks;

    public static FakeRoomRows empty() {
        return new FakeRoomRows();
    }

    public FakeRoomRows room(String roomId, String hostUid) {
        locked.add(Room.builder().id(roomId).name("Шляпа").phase("setup").createdBy(hostUid).build());
        return this;
    }

    public int locks() {
        return locks;
    }

    @Override
    public Optional<Room> lock(String roomId) {
        locks++;
        return locked.stream().filter(room -> room.getId().equals(roomId)).findFirst();
    }

    @Override
    public List<Room> findAll(Collection<String> roomIds) {
        return locked.stream().filter(room -> roomIds.contains(room.getId())).toList();
    }
}
