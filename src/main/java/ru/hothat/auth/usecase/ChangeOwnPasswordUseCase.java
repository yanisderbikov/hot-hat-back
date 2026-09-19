package ru.hothat.auth.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.auth.api.dto.ChangeOwnPasswordRequestDTO;
import ru.hothat.auth.store.IdentityStore;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;

/**
 * Сменить свой пароль, подтвердив текущий.
 *
 * <p>Неверный текущий пароль — это {@code PASSWORD_MISMATCH} и 403, а не
 * общий {@code INVALID_CREDENTIALS} с 401: сессия жива, и текст «войдите
 * снова» здесь был бы прямым враньём (замечание D3 аудита).
 *
 * <p>Смена пароля выкидывает все прежние сессии — это ожидаемое поведение и
 * единственная причина, по которой она вообще защищает: поколение токенов
 * поднимается тем же оператором, что меняет хеш, а живые refresh гасятся
 * следом.
 */
@Service
@RequiredArgsConstructor
public class ChangeOwnPasswordUseCase {

    private final IdentityStore identities;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void run(HotHatUser user, ChangeOwnPasswordRequestDTO request) {
        if (!identities.passwordMatches(user.uid(), request.currentPassword())) {
            throw ApiException.of("PASSWORD_MISMATCH", 403);
        }
        identities.replacePassword(user.uid(), request.newPassword());
    }
}
