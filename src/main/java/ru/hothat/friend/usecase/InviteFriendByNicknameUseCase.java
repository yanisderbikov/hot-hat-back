package ru.hothat.friend.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.friend.api.dto.InviteFriendByNicknameRequestDTO;
import ru.hothat.friend.api.dto.InvitedPlayerView;
import ru.hothat.friend.api.dto.SentFriendRequestResponseDTO;

/** Позвать в друзья игрока, названного ником. */
@Service
@RequiredArgsConstructor
public class InviteFriendByNicknameUseCase {

    private final FriendRequestSubmission submission;
    private final PlayerCardPort cards;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public SentFriendRequestResponseDTO run(HotHatUser user, InviteFriendByNicknameRequestDTO request) {
        // Ник разрешаем до заявки: по нику VASYA заявка уходит игроку vasya,
        // и ответ 201 обязан назвать второе. Поиск заодно проверяет, что такой
        // игрок есть, — отдельная проверка существования была бы вторым
        // чтением того же профиля.
        String addresseeUid = cards.requireUidByNickname(request.nickname());
        submission.submit(user, addresseeUid);
        String addresseeNickname = cards.nicknameOf(addresseeUid);
        return new SentFriendRequestResponseDTO(new InvitedPlayerView(addresseeUid, addresseeNickname));
    }
}
