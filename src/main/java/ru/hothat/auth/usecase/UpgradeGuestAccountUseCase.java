package ru.hothat.auth.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.auth.api.dto.UpgradeGuestAccountRequestDTO;
import ru.hothat.auth.api.dto.UpgradedGuestAccountResponseDTO;
import ru.hothat.auth.store.IdentityStore;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;

/**
 * Превратить гостя в полноценную учётку, сохранив uid и всю статистику.
 *
 * <p>Личность берётся из токена: параметра, куда подставить чужой uid, у
 * операции нет — это и есть починка старого {@code POST /api/auth/link},
 * который принимал uid телом запроса.
 *
 * <p>«Он ещё гость?» проверяет условие в самом {@code UPDATE}, а не чтение
 * перед записью: без него два одновременных апгрейда одной гостевой учётки
 * проходили оба (дыра A4). Второй получает {@code ALREADY_REGISTERED}.
 *
 * <p>Карточка игрока не трогается: ник, аватар и дивизион гость выбрал ещё
 * гостем, и апгрейд их не отменяет — в этом весь его смысл.
 */
@Service
@RequiredArgsConstructor
public class UpgradeGuestAccountUseCase {

    private final IdentityStore identities;
    private final SessionIssuer issuer;

    @PreAuthorize("hasRole('GUEST')")
    @Transactional
    public UpgradedGuestAccountResponseDTO run(HotHatUser guest,
                                               UpgradeGuestAccountRequestDTO request,
                                               String userAgent) {
        if (identities.emailTaken(request.email())) {
            throw ApiException.of("EMAIL_TAKEN", 409);
        }
        if (!identities.upgradeGuest(guest.uid(), request.email(), request.password())) {
            throw ApiException.of("ALREADY_REGISTERED", 409);
        }
        // Гостевая пара гасится: у учётки появился пароль, и токен, выданный
        // до его появления, больше не описывает того, кто им владеет. Новую
        // пару этот же ответ и отдаёт, поэтому человек ничего не теряет.
        identities.revokeAccess(guest.uid(), IdentityStore.REASON_LOGOUT_ALL);
        IdentityStore.Account account = identities.byUid(guest.uid())
                .orElseThrow(() -> ApiException.of("USER_NOT_FOUND", 404));
        return new UpgradedGuestAccountResponseDTO(
                issuer.open(account, userAgent, null), issuer.view(account));
    }
}
