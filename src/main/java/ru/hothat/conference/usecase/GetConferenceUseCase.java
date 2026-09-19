package ru.hothat.conference.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.conference.api.dto.ConferenceResponseDTO;
import ru.hothat.config.HotHatUser;

/**
 * Показать видео-чат участнику.
 *
 * <p>Тот же сценарий кормит канал {@code /ws/v2/conference/{id}}: право
 * перепроверяется на каждом кадре, и выгнанный перестаёт получать кадры сразу.
 */
@Service
@RequiredArgsConstructor
public class GetConferenceUseCase {

    private final ConferenceAccess access;
    private final ConferenceProjections projections;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public ConferenceResponseDTO run(HotHatUser user, String conferenceId) {
        return new ConferenceResponseDTO(projections.view(access.requireParticipant(conferenceId, user.uid())));
    }
}
