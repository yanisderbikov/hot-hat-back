package ru.hothat.friend.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.config.ApiException;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.friend.api.dto.InviteFriendByUidRequestDTO;
import ru.hothat.friend.api.dto.InvitedPlayerView;
import ru.hothat.friend.api.dto.SentFriendRequestByUidResponseDTO;

/** Позвать в друзья игрока, которого отправитель уже видит на экране. */
@Service
@RequiredArgsConstructor
public class InviteFriendByUidUseCase {

    private final FriendRequestSubmission submission;
    private final PlayerCardPort cards;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public SentFriendRequestByUidResponseDTO run(HotHatUser user, InviteFriendByUidRequestDTO request) {
        String hint = request.nicknameHint() == null ? "" : request.nicknameHint().trim();
        // Сначала «а есть ли такой игрок», потом заявка: несуществующему
        // адресату полагается честный 404, а не запасное имя playerXXXXXX,
        // которое придумалось бы при разрешении ника.
        PlayerCardPort.Card addressee = cards.card(request.friendUid())
                .orElseThrow(() -> ApiException.of("PLAYER_NOT_FOUND", 404));
        submission.submit(user, request.friendUid());
        // Подсказка из тела запроса больше ни на что не влияет: имя есть в
        // карточке, и оно нынешнее, а подсказка — то, что клиент видел раньше.
        String addresseeNickname = addressee.nickname();
        return new SentFriendRequestByUidResponseDTO(
                new InvitedPlayerView(request.friendUid(), addresseeNickname));
    }
}
