package ru.hothat.friend.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.friend.store.FriendshipStore;

/**
 * Общее тело двух приглашений в друзья: по нику и по игроку.
 *
 * <p>Отдельный класс, а не вызов чужого сценария: сценарию запрещено звать
 * сценарий (§7.2), а правило «кого можно звать в друзья» у обоих адресов одно,
 * и две его копии разошлись бы на первой же правке. Адресат сюда приходит уже
 * найденным — где его искали, ником или идентификатором, решает вызывающий.
 */
@Component
@RequiredArgsConstructor
class FriendRequestSubmission {

    private final FriendshipStore friendships;

    /**
     * Завести заявку от игрока к названному адресату.
     *
     * <p>Коды ошибок оставлены прежними: экран друзей уже умеет их разбирать.
     * Последняя проверка — не здесь, а в базе: уникальный индекс не даёт двум
     * встречным приглашениям, отправленным одновременно, стать двумя живыми
     * заявками, и тогда проигравший получает тот же {@code REQUEST_EXISTS},
     * что и опоздавший на секунду.
     */
    long submit(HotHatUser user, String addresseeUid) {
        if (addresseeUid == null || addresseeUid.isBlank() || addresseeUid.equals(user.uid())) {
            throw ApiException.of("FRIEND_SELF");
        }
        if (friendships.areFriends(user.uid(), addresseeUid)) {
            throw ApiException.of("ALREADY_FRIENDS", 409);
        }
        if (friendships.hasPendingRequest(user.uid(), addresseeUid)) {
            throw ApiException.of("REQUEST_EXISTS", 409);
        }
        try {
            return friendships.openRequest(user.uid(), addresseeUid);
        } catch (DataIntegrityViolationException race) {
            throw ApiException.of("REQUEST_EXISTS", 409);
        }
    }
}
