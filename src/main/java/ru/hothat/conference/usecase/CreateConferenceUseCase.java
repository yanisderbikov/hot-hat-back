package ru.hothat.conference.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.conference.api.dto.ConferenceResponseDTO;
import ru.hothat.conference.store.ConferenceStore;
import ru.hothat.config.HotHatUser;

import java.time.Clock;
import java.time.Instant;

/**
 * Завести видео-чат и стать его хозяином.
 *
 * <p>Ничего, кроме личности, для этого не нужно: ни обоймы мемов, ни
 * дивизиона — созвон не игра. Идентификатор и срок ставит сервер.
 */
@Service
@RequiredArgsConstructor
public class CreateConferenceUseCase {

    private final ConferenceStore store;
    private final ConferenceAccess access;
    private final ConferenceProjections projections;
    private final Clock clock;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public ConferenceResponseDTO run(HotHatUser user) {
        String id = store.create(user.uid(), Instant.now(clock));
        return new ConferenceResponseDTO(projections.view(access.requireParticipant(id, user.uid())));
    }
}
