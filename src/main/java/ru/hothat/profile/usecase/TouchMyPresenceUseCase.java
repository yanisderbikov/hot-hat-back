package ru.hothat.profile.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.PresenceHeartbeatResponseDTO;
import ru.hothat.profile.store.ProfileStore;

/**
 * Отметить, что игрок сейчас в портале.
 *
 * <p>Отметка — это запись времени, поэтому адрес отвечает не 204: клиенту
 * возвращаются часы сервера, по которым он сверяет свои таймеры. Своим
 * часам доверять нельзя — «онлайн» считается по серверному времени.
 *
 * <p>Пишется теперь отдельная строка присутствия, а не строка учётки целиком.
 * Раньше пинг раз в полминуты переписывал ту же страницу, где лежат хеш
 * пароля, согласия и аватар на 90 килобайт.
 */
@Service
@RequiredArgsConstructor
public class TouchMyPresenceUseCase {

    private final ProfileStore profiles;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public PresenceHeartbeatResponseDTO run(HotHatUser user) {
        profiles.touchPresence(user.uid());
        return new PresenceHeartbeatResponseDTO(System.currentTimeMillis());
    }
}
