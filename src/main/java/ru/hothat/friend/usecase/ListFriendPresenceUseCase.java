package ru.hothat.friend.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.friend.api.dto.FriendPresenceResponseDTO;
import ru.hothat.friend.api.dto.FriendPresenceView;
import ru.hothat.friend.store.FriendshipStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Показать, кто из друзей сейчас в сети.
 *
 * <p>Самый частый адрес области: экран друзей опрашивает его раз в минуту.
 * Поэтому берётся только то, что нужно ответу, — связи и отметки времени;
 * ни ники, ни аватары, ни заявки здесь не читаются. Ник не спрашивается ни
 * разу, а значит и запасной путь разрешения имени не срабатывает.
 */
@Service
@RequiredArgsConstructor
public class ListFriendPresenceUseCase {

    /**
     * Окно «в сети» — 90 секунд. Это порог, по которому интерфейс рисует
     * «в сети» сегодня ({@code friends/friends.js:11}); переносим его как есть,
     * чтобы переезд на v2 не поменял картинку. Сводка на главной считает по
     * 130 секундам ({@code ProfileServiceImpl:559}) — два разных вопроса,
     * «сколько людей в игре» и «горит ли точка у друга», и сводить их к
     * одному числу здесь не нужно.
     */
    private static final long ONLINE_WINDOW_MS = 90_000L;

    private final FriendshipStore friendships;
    private final FriendProfileDirectory profileDirectory;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public FriendPresenceResponseDTO run(HotHatUser user) {
        List<String> friendUids = friendships.friendUids(user.uid(), FriendReadLimits.FRIENDS);

        FriendProfileDirectory.Snapshot profiles = profileDirectory.load(friendUids);
        // Присутствие считает сервер: у клиента часы могут уехать, и тогда все
        // друзья одинаково «давно не в сети».
        long nowMs = System.currentTimeMillis();
        List<FriendPresenceView> items = new ArrayList<>(friendUids.size());
        for (String uid : friendUids) {
            long lastSeenAtMs = profiles.lastSeenAtMs(uid);
            items.add(new FriendPresenceView(uid, lastSeenAtMs,
                    lastSeenAtMs > 0 && nowMs - lastSeenAtMs < ONLINE_WINDOW_MS));
        }
        return new FriendPresenceResponseDTO(items, null, FriendReadLimits.FRIENDS, nowMs, ONLINE_WINDOW_MS);
    }
}
