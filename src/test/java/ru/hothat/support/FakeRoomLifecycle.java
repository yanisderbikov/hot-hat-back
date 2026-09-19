package ru.hothat.support;

import ru.hothat.game.port.RoomLifecyclePort;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Комната глазами области партии: хозяин, места игроков и места зрителей.
 *
 * <p>Пишущая половина порта записывает вызовы, а не притворяется работающей:
 * правам ничего писать не положено, и любая запись отсюда — сама по себе
 * находка.
 */
public final class FakeRoomLifecycle implements RoomLifecyclePort {

    private String hostUid;
    private final Set<String> members = new LinkedHashSet<>();
    private final Set<String> spectators = new LinkedHashSet<>();
    private final List<String> writes = new ArrayList<>();
    private int membershipReads;

    public static FakeRoomLifecycle empty() {
        return new FakeRoomLifecycle();
    }

    public FakeRoomLifecycle host(String uid) {
        hostUid = uid;
        members.add(uid);
        return this;
    }

    public FakeRoomLifecycle member(String uid) {
        members.add(uid);
        return this;
    }

    public FakeRoomLifecycle spectator(String uid) {
        spectators.add(uid);
        return this;
    }

    /** Сколько раз спрашивали состав: по этому счёту видно кеш предикатов. */
    public int membershipReads() {
        return membershipReads;
    }

    public List<String> writes() {
        return List.copyOf(writes);
    }

    @Override
    public Optional<RoomLifecycleView> find(String roomId) {
        return Optional.of(new RoomLifecycleView(roomId, "Шляпа", hostUid, "setup", 0, "classic",
                false, false, false, "ru", "ru", 10, 60, List.of()));
    }

    @Override
    public List<RoomSeatView> seats(String roomId) {
        return members.stream().map(uid -> new RoomSeatView(uid, uid, null, false, 0L)).toList();
    }

    @Override
    public Optional<RoomSeatView> seat(String roomId, String uid) {
        return members.contains(uid)
                ? Optional.of(new RoomSeatView(uid, uid, null, false, 0L))
                : Optional.empty();
    }

    @Override
    public boolean isHost(String roomId, String uid) {
        membershipReads++;
        return uid != null && uid.equals(hostUid);
    }

    @Override
    public boolean isMember(String roomId, String uid) {
        membershipReads++;
        return members.contains(uid);
    }

    @Override
    public boolean isSpectator(String roomId, String uid) {
        membershipReads++;
        return spectators.contains(uid);
    }

    @Override
    public void onMatchStarted(String roomId, int gameNumber, List<String> playerUids) {
        writes.add("onMatchStarted");
    }

    @Override
    public void onMatchFinished(String roomId, int gameNumber, String reason) {
        writes.add("onMatchFinished");
    }

    @Override
    public void closeRoom(String roomId, String reason) {
        writes.add("closeRoom");
    }

    @Override
    public void touchSeat(String roomId, String uid, long atMs) {
        writes.add("touchSeat");
    }

    @Override
    public void dropSeatPresence(String roomId, String uid) {
        writes.add("dropSeatPresence");
    }
}
