package ru.hothat.conference.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.conference.api.dto.ConferenceMessagesResponseDTO;
import ru.hothat.conference.domain.ConferenceRules;
import ru.hothat.conference.store.ConferenceStore;
import ru.hothat.config.HotHatUser;

/**
 * Лента чата видео-чата для участника.
 *
 * <p>Тот же сценарий кормит канал {@code /ws/v2/conference/{id}}: окно
 * одного размера, ссылки на вложения подписаны заново.
 */
@Service
@RequiredArgsConstructor
public class ReadConferenceMessagesUseCase {

    private final ConferenceAccess access;
    private final ConferenceStore store;
    private final ConferenceProjections projections;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public ConferenceMessagesResponseDTO run(HotHatUser user, String conferenceId) {
        access.requireParticipant(conferenceId, user.uid());
        return new ConferenceMessagesResponseDTO(
                projections.messages(store.messages(conferenceId, ConferenceRules.CHAT_WINDOW)),
                ConferenceRules.CHAT_WINDOW);
    }
}
