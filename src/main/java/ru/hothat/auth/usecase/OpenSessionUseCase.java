package ru.hothat.auth.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.admin.spi.BanPort;
import ru.hothat.auth.api.dto.IssuedSessionResponseDTO;
import ru.hothat.auth.api.dto.OpenSessionRequestDTO;
import ru.hothat.auth.store.IdentityStore;
import ru.hothat.config.ApiException;

/**
 * Открыть сессию по почте и паролю.
 *
 * <p>Открыт всем: до входа токена нет. Правило записано и здесь, а не только
 * в конфигурации маршрутов, — иначе право адреса пришлось бы искать в двух
 * местах, и одно из них однажды соврало бы.
 *
 * <p>Отказ приходит кодом {@code INVALID_CREDENTIALS} (401) и одинаков для
 * несуществующего адреса, отсутствующего пароля и неверного пароля — иначе
 * форма входа перечисляет зарегистрированные почты. Различать эти три случая
 * не умеет и сам {@code IdentityStore.authenticate}: он отвечает пустотой.
 *
 * <p>Заблокированного не пускают до выдачи пары. Проверка идёт у области
 * модерации, а не по флагу в учётке: флага там больше нет, и правда о бане
 * лежит в одном месте.
 */
@Service
@RequiredArgsConstructor
public class OpenSessionUseCase {

    private final IdentityStore identities;
    private final BanPort bans;
    private final SessionIssuer issuer;

    @PreAuthorize("permitAll()")
    @Transactional
    public IssuedSessionResponseDTO run(OpenSessionRequestDTO request, String userAgent) {
        IdentityStore.Account account = identities.authenticate(request.email(), request.password())
                .orElseThrow(() -> ApiException.of("INVALID_CREDENTIALS", 401));
        if (bans.banned(account.uid())) {
            throw ApiException.of("USER_BANNED", 403);
        }
        return new IssuedSessionResponseDTO(
                issuer.open(account, userAgent, null), issuer.view(account));
    }
}
