package ru.hothat.auth.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.auth.api.dto.RevokeSessionRequestDTO;
import ru.hothat.auth.store.IdentityStore;

/**
 * Выйти с этого устройства.
 *
 * <p>Открыт всем: выходят предъявлением своего refresh-токена, а access к
 * этому моменту мог уже истечь. Неизвестный токен ответ не отличает от
 * известного — иначе адрес превращается в проверялку чужих токенов.
 */
@Service
@RequiredArgsConstructor
public class RevokeSessionUseCase {

    private final IdentityStore identities;

    @PreAuthorize("permitAll()")
    @Transactional
    public void run(RevokeSessionRequestDTO request) {
        identities.closeSession(request.refreshToken(), IdentityStore.REASON_LOGOUT);
    }
}
