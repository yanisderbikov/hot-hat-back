package ru.hothat.auth.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.auth.api.dto.CompletePasswordResetRequestDTO;
import ru.hothat.auth.store.IdentityStore;
import ru.hothat.config.ApiException;

/**
 * Задать новый пароль по ссылке восстановления.
 *
 * <p>Одноразовость держит условный {@code UPDATE … WHERE used_at IS NULL} в
 * хранилище, а не чтение перед записью: два одновременных перехода по одной
 * ссылке меняли пароль дважды, и второй из них — с чужим значением.
 *
 * <p>Новый пароль отзывает все прежние сессии: тот, кто увёл аккаунт, теряет
 * его в тот же миг.
 */
@Service
@RequiredArgsConstructor
public class CompletePasswordResetUseCase {

    private final IdentityStore identities;

    @PreAuthorize("permitAll()")
    @Transactional
    public void run(CompletePasswordResetRequestDTO request) {
        String uid = identities.consumePasswordReset(request.token())
                .orElseThrow(() -> ApiException.of("INVALID_RESET_TOKEN", 400));
        identities.replacePassword(uid, request.newPassword());
    }
}
