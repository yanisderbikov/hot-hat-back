package ru.hothat.friend.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.friend.api.dto.AcceptedFriendRequestResponseDTO;
import ru.hothat.friend.store.FriendshipStore;

/**
 * Принять входящую заявку в друзья.
 *
 * <p>Ответ и дружба записываются одной транзакцией: «заявка принята, а друзей
 * нет» — состояние, из которого экран не выходит никогда, потому что второй
 * раз ту же заявку принять уже нельзя.
 */
@Service
@RequiredArgsConstructor
public class AcceptFriendRequestUseCase {

    private final FriendshipStore friendships;
    private final PlayerCardPort cards;
    private final FriendRequestAuthz friendAuthz;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public AcceptedFriendRequestResponseDTO run(HotHatUser user, long requestId) {
        friendAuthz.assertRecipient(user, requestId);

        FriendshipStore.RequestRow request = friendships.request(requestId)
                .orElseThrow(() -> ApiException.of("REQUEST_NOT_FOUND", 404));
        // Отвечают только на живую заявку: повторное согласие не должно
        // переставлять отметку времени ответа и заводить дружбу заново.
        if (!friendships.answerRequest(requestId, true)) {
            throw ApiException.of("REQUEST_NOT_FOUND", 404);
        }
        friendships.link(request.requesterUid(), request.addresseeUid());

        // Ответ 201 обязан назвать, с кем игрок теперь дружит: имя берётся из
        // карточки отправителя, копии ника в заявке больше нет.
        return new AcceptedFriendRequestResponseDTO(requestId, request.requesterUid(),
                cards.nicknameOf(request.requesterUid()));
    }
}
