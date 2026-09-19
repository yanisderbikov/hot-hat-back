package ru.hothat.support;

import ru.hothat.friend.spi.FriendshipPort;

import java.util.ArrayList;
import java.util.List;

/** Дружба в памяти: пара без порядка сторон, как и в настоящей таблице. */
public final class FakeFriendships implements FriendshipPort {

    private final List<String> pairs = new ArrayList<>();

    public static FakeFriendships empty() {
        return new FakeFriendships();
    }

    public FakeFriendships friends(String uidA, String uidB) {
        pairs.add(key(uidA, uidB));
        return this;
    }

    @Override
    public boolean areFriends(String uidA, String uidB) {
        return pairs.contains(key(uidA, uidB));
    }

    @Override
    public List<String> friendUids(String uid, int limit) {
        return List.of();
    }

    private static String key(String uidA, String uidB) {
        return uidA.compareTo(uidB) <= 0 ? uidA + "|" + uidB : uidB + "|" + uidA;
    }
}
