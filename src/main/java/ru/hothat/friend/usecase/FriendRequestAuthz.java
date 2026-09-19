package ru.hothat.friend.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.friend.store.FriendshipStore;

/**
 * Право отвечать на заявку принадлежит адресату.
 *
 * <p>Проверка вынесена из сценария в отдельное правило, потому что двум
 * сценариям — согласию и отказу — нужен один и тот же ответ на вопрос «твоя ли
 * это заявка», и повторять его в каждом значило бы завести два места, которые
 * со временем разойдутся.
 *
 * <p>Чужой или несуществующий идентификатор даёт один и тот же ответ:
 * по номеру заявки нельзя узнать, существует ли она.
 */
@Component("friendAuthz")
@RequiredArgsConstructor
public class FriendRequestAuthz {

    private final FriendshipStore friendships;

    /**
     * Тот же вопрос, но заданный из тела сценария. Через {@code @PreAuthorize}
     * отказ уходит наружу как {@code AccessDeniedException}, а его общий
     * обработчик объясняет любой отказ нехваткой прав администратора — на
     * странице друзей это прямая неправда. Здесь отказ называет себя сам.
     */
    public void assertRecipient(HotHatUser user, long requestId) {
        boolean mine = friendships.request(requestId)
                .map(FriendshipStore.RequestRow::addresseeUid)
                .filter(user.uid()::equals)
                .isPresent();
        if (!mine) {
            throw ApiException.of("REQUEST_FORBIDDEN", 403);
        }
    }
}
