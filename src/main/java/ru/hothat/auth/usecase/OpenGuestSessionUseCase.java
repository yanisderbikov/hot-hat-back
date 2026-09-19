package ru.hothat.auth.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.auth.api.dto.IssuedGuestSessionResponseDTO;
import ru.hothat.auth.api.dto.OpenGuestSessionRequestDTO;
import ru.hothat.auth.store.IdentityStore;
import ru.hothat.config.ApiException;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.profile.spi.ProfileCommandPort;

/**
 * Открыть гостевую сессию.
 *
 * <p>Гостю выдаётся полноценная пара токенов, и с приходом области {@code auth}
 * она наконец работает: до сих пор access-токен гостя отвергался на любом
 * защищённом маршруте (аудит A7), а refresh при этом обновлялся бесконечно,
 * создавая видимость живой сессии. Роль {@code ROLE_GUEST} открывает ровно
 * четыре вещи из §8 плана: лобби, превью комнаты, апгрейд учётки и правовые
 * согласия.
 *
 * <p>Тела может не быть вовсе: клиент заводит гостя ещё до того, как игрок
 * что-либо ввёл. Тогда имя придумывает сервер — {@code Guest######}, как и
 * раньше. Ни почты, ни пароля у гостевой учётки нет, и это держит ограничение
 * базы, а не соглашение: завести гостя с паролем нельзя, поэтому апгрейд
 * всегда знает, кем человек был.
 */
@Service
@RequiredArgsConstructor
public class OpenGuestSessionUseCase {

    private final IdentityStore identities;
    private final PlayerCardPort cards;
    private final ProfileCommandPort profiles;
    private final SessionIssuer issuer;

    @PreAuthorize("permitAll()")
    @Transactional
    public IssuedGuestSessionResponseDTO run(OpenGuestSessionRequestDTO request, String userAgent) {
        String requested = request == null ? null : request.nickname();
        if (requested != null && !requested.isBlank() && cards.nicknameTaken(requested)) {
            throw ApiException.of("NICKNAME_TAKEN", 409);
        }
        IdentityStore.Account account = identities.openGuest();
        String nickname = requested == null || requested.isBlank()
                ? profiles.freeNicknameFrom(null, account.uid())
                : requested.trim();
        profiles.createCard(account.uid(), nickname, null);
        return new IssuedGuestSessionResponseDTO(
                issuer.open(account, userAgent, null), issuer.view(account));
    }
}
