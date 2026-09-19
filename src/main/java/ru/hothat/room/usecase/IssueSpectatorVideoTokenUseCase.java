package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.room.api.dto.SpectatorVideoTokenResponseDTO;

/**
 * Выдать зрителю видеотокен.
 *
 * <p>Токен только на приём: публиковать дорожки из зала нельзя. Приватную
 * комнату он не откроет — там зрителей не бывает вовсе.
 *
 * <p>Тела у запроса нет: комната в адресе, роль задана самим адресом, имя
 * сервер берёт из зрительского места.
 */
@Service
@RequiredArgsConstructor
public class IssueSpectatorVideoTokenUseCase {

    private final RoomAccessGuard roomAuthz;
    private final RoomVideoTokens videoTokens;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public SpectatorVideoTokenResponseDTO run(HotHatUser user, String roomId) {
        roomAuthz.requireSpectator(user, roomId);
        // Сессии превью нет: этот зритель сидит в зале комнаты, а не наводит
        // курсор на её карточку в витрине.
        RoomVideoTokens.Issued issued = videoTokens.forSpectator(user, roomId, null);
        return new SpectatorVideoTokenResponseDTO(
                issued.serverUrl(), issued.participantToken(),
                issued.participantIdentity(), RoomVideoTokens.credentials(issued));
    }
}
