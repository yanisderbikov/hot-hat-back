package ru.hothat.auth.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.auth.api.dto.MyAccountResponseDTO;
import ru.hothat.auth.store.IdentityStore;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;

/**
 * Показать свою учётную запись.
 *
 * <p>Гость видит свою так же, как зарегистрированный: без этого адреса он не
 * может даже узнать, под каким именем сидит, — а имя ему выдал сервер.
 *
 * <p>Личность берётся из токена и только из него. Читается запись по своему же
 * идентификатору, поэтому «чужая учётка» здесь непредставима: параметра, куда
 * подставить чужой uid, у операции нет.
 *
 * <p>Наружу уходит один бит про пароль: есть он или нет. Самого хеша тут не
 * бывает вовсе — он не покидает {@code IdentityStore} (находка B4).
 */
@Service
@RequiredArgsConstructor
public class GetMyAccountUseCase {

    private final IdentityStore identities;
    private final SessionIssuer issuer;

    @PreAuthorize("hasAnyRole('USER','GUEST')")
    public MyAccountResponseDTO run(HotHatUser user) {
        IdentityStore.Account account = identities.byUid(user.uid())
                .orElseThrow(() -> ApiException.of("USER_NOT_FOUND", 404));
        return new MyAccountResponseDTO(
                issuer.view(account),
                account.passwordSet(),
                account.memberSinceMs(),
                account.passwordUpdatedAtMs());
    }
}
