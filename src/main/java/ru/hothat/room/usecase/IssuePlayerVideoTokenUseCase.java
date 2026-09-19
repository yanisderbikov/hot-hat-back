package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.room.api.dto.IssuePlayerVideoTokenRequestDTO;
import ru.hothat.room.api.dto.PlayerVideoTokenResponseDTO;

/**
 * Выдать игроку видеотокен.
 *
 * <p>Заменяет {@code POST /api/token} с {@code role=player}
 * ({@code livekit.js:541}). Отдельный адрес вместо поля {@code role} в теле:
 * у игрока и у зрителя разные права участника, разные отказы и разное место в
 * комнате, и различать их дискриминатором внутри тела значило бы описывать
 * одной схемой две операции.
 *
 * <p>Транзакция на чтение: сам токен ничего не пишет, но проверка места и
 * дивизиона внутри движка читает три строки, и все три должны приехать из
 * одного снимка.
 */
@Service
@RequiredArgsConstructor
public class IssuePlayerVideoTokenUseCase {

    private final RoomAccessGuard roomAuthz;
    private final RoomVideoTokens videoTokens;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public PlayerVideoTokenResponseDTO run(HotHatUser user, String roomId,
                                           IssuePlayerVideoTokenRequestDTO request) {
        roomAuthz.requireMember(user, roomId);
        RoomVideoTokens.Issued issued = videoTokens.forPlayer(user, roomId,
                request == null ? null : request.participantIdentity());
        return new PlayerVideoTokenResponseDTO(
                issued.serverUrl(), issued.participantToken(),
                issued.participantIdentity(), RoomVideoTokens.credentials(issued));
    }
}
