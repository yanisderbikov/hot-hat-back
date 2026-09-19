package ru.hothat.auth.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.admin.spi.BanPort;
import ru.hothat.auth.api.dto.RenewSessionRequestDTO;
import ru.hothat.auth.api.dto.RenewedSessionResponseDTO;
import ru.hothat.auth.store.IdentityStore;
import ru.hothat.config.ApiException;

/**
 * Обменять refresh-токен на новую пару.
 *
 * <p>Открыт всем, и это не дыра: предъявленный refresh-токен и есть
 * доказательство личности. Access-токена в этот момент может уже не быть —
 * за ним сюда и приходят.
 *
 * <p>Пара ротируется целиком: прежний refresh гасится, поэтому украденный
 * токен годится ровно на один обмен. Повторное предъявление уже погашенного
 * токена — почти всегда признак кражи, и оно гасит всю цепочку: и остальные
 * refresh-токены владельца, и поколение, отсекающее выданные access-токены
 * (аудит A11). Цена ошибки несимметрична: честному человеку придётся войти
 * заново, вору — остаться ни с чем.
 *
 * <p>Цепочка гасится в своей транзакции ({@link SessionChainRevoker}): ответ
 * здесь — исключение, а оно откатило бы отзыв вместе со всем остальным.
 */
@Service
@RequiredArgsConstructor
public class RenewSessionUseCase {

    private final IdentityStore identities;
    private final BanPort bans;
    private final SessionChainRevoker chainRevoker;
    private final SessionIssuer issuer;

    @PreAuthorize("permitAll()")
    @Transactional
    public RenewedSessionResponseDTO run(RenewSessionRequestDTO request, String userAgent) {
        IdentityStore.RefreshRow presented = identities.findSession(request.refreshToken())
                .orElseThrow(() -> ApiException.of("INVALID_REFRESH_TOKEN", 401));
        if (presented.revoked()) {
            // Реюз-детект. Токен известен, но уже погашен — значит, его
            // предъявляют второй раз. Своих причин для этого нет: клиент
            // получает новый токен тем же ответом, которым гасится прежний.
            chainRevoker.revokeEverything(presented.uid(), presented.familyId());
            throw ApiException.of("INVALID_REFRESH_TOKEN", 401);
        }
        if (!presented.usable()) {
            throw ApiException.of("INVALID_REFRESH_TOKEN", 401);
        }
        IdentityStore.Account account = identities.byUid(presented.uid())
                .orElseThrow(() -> ApiException.of("INVALID_REFRESH_TOKEN", 401));
        if (bans.banned(account.uid())) {
            throw ApiException.of("USER_BANNED", 403);
        }
        return new RenewedSessionResponseDTO(
                issuer.rotate(account, request.refreshToken(), userAgent, null),
                issuer.view(account));
    }
}
