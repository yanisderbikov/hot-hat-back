package ru.hothat.friend.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.friend.store.FriendshipStore;

import java.util.List;

/**
 * Реализация read-порта дружбы: живёт у владельца данных, как велит §7.3
 * плана.
 */
@Component
@RequiredArgsConstructor
public class FriendshipDirectory implements FriendshipPort {

    private final FriendshipStore friendships;

    @Override
    public List<String> friendUids(String uid, int limit) {
        if (uid == null || uid.isBlank() || limit <= 0) {
            return List.of();
        }
        return friendships.friendUids(uid, limit);
    }

    @Override
    public boolean areFriends(String uidA, String uidB) {
        if (uidA == null || uidB == null || uidA.isBlank() || uidB.isBlank() || uidA.equals(uidB)) {
            return false;
        }
        return friendships.areFriends(uidA, uidB);
    }
}
