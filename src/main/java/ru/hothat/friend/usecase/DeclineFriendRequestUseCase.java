package ru.hothat.friend.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.friend.store.FriendshipStore;

/**
 * Отклонить входящую заявку в друзья.
 *
 * <p>Ответа нет: отказ ничего не создаёт, а адрес уже назвал и заявку,
 * и исход.
 */
@Service
@RequiredArgsConstructor
public class DeclineFriendRequestUseCase {

    private final FriendshipStore friendships;
    private final FriendRequestAuthz friendAuthz;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void run(HotHatUser user, long requestId) {
        friendAuthz.assertRecipient(user, requestId);

        // Отвечают только на живую заявку: отклонить уже принятую нельзя,
        // иначе дружба осталась бы при отклонённой заявке.
        if (!friendships.answerRequest(requestId, false)) {
            throw ApiException.of("REQUEST_NOT_FOUND", 404);
        }
    }
}
