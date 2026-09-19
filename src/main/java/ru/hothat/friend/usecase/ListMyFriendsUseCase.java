package ru.hothat.friend.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.friend.api.dto.FriendCardView;
import ru.hothat.friend.api.dto.MyFriendsPageResponseDTO;
import ru.hothat.friend.api.dto.MyFriendsQueryDTO;
import ru.hothat.friend.store.FriendshipStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Показать круг друзей игрока.
 *
 * <p>Читает только свои связи и профили тех, кто в них назван: два запроса за
 * своими данными и один за профилями, сколько бы друзей ни было.
 *
 * <p>Дружба лежит в {@code v2.friendship}, где пара — это ключ, а не склеенная
 * строка: «кто мои друзья» больше не требует ни разбора {@code pair}, ни
 * чтения jsonb со списком участников.
 */
@Service
@RequiredArgsConstructor
public class ListMyFriendsUseCase {

    private final FriendshipStore friendships;
    private final FriendProfileDirectory profileDirectory;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public MyFriendsPageResponseDTO run(HotHatUser user, MyFriendsQueryDTO query) {
        // Единственное место дефолта: тело запроса может не прийти вовсе,
        // и «не задано» не должно разъезжаться по контроллеру и сценарию.
        int limit = query == null || query.limit() == null
                ? FriendReadLimits.FRIENDS
                : query.limit();

        // Предел уезжает в базу, а не применяется после чтения.
        List<String> friendUids = friendships.friendUids(user.uid(), limit);

        FriendProfileDirectory.Snapshot profiles = profileDirectory.load(friendUids);
        List<FriendCardView> items = new ArrayList<>(friendUids.size());
        for (String uid : friendUids) {
            items.add(new FriendCardView(
                    uid,
                    // Подсказки имени у связи нет: в ней хранятся только игроки.
                    profiles.nickname(uid, ""),
                    profiles.avatarDataUrl(uid),
                    profiles.lastSeenAtMs(uid),
                    profiles.divisionLanguage(uid)));
        }
        return new MyFriendsPageResponseDTO(items, null, limit);
    }
}
