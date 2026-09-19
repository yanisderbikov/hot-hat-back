package ru.hothat.auth.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.auth.api.dto.RevokedSessionsResponseDTO;
import ru.hothat.auth.store.IdentityStore;
import ru.hothat.config.HotHatUser;

import java.time.Instant;

/**
 * Выйти со всех устройств, включая это.
 *
 * <p>Гасятся и живые refresh-токены, и поколение: без второго уже выданные
 * access-токены работали бы ещё свои пятнадцать минут, то есть «выход
 * отовсюду» ничего бы не значил в ближайшую четверть часа.
 */
@Service
@RequiredArgsConstructor
public class RevokeAllSessionsUseCase {

    private final IdentityStore identities;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public RevokedSessionsResponseDTO run(HotHatUser user) {
        identities.revokeAccess(user.uid(), IdentityStore.REASON_LOGOUT_ALL);
        // Время берём серверное: у клиента часы могут уехать, а по этой метке
        // он решает, какие свои сохранённые токены выбросить.
        return new RevokedSessionsResponseDTO(true, Instant.now().toEpochMilli());
    }
}
