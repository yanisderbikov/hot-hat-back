package ru.hothat.conference.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.conference.api.dto.PostConferenceMessageRequestDTO;
import ru.hothat.conference.api.dto.SentConferenceMessageResponseDTO;
import ru.hothat.conference.domain.ConferenceRules;
import ru.hothat.conference.store.ConferenceStore;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.realtime.spi.RealtimeChangeBus;

import java.time.Clock;
import java.time.Instant;

/** Написать в чат видео-чата. */
@Service
@RequiredArgsConstructor
public class PostConferenceMessageUseCase {

    private final ConferenceAccess access;
    private final ConferenceStore store;
    private final ConferenceProjections projections;
    private final RealtimeChangeBus changes;
    private final Clock clock;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public SentConferenceMessageResponseDTO run(HotHatUser user, String conferenceId,
                                                PostConferenceMessageRequestDTO request) {
        access.requireParticipant(conferenceId, user.uid());
        String text = ConferenceText.clean(request.text());
        if (text.isEmpty()) {
            throw ApiException.of("CONFERENCE_CHAT_EMPTY", 400);
        }
        Instant now = Instant.now(clock);
        long id = store.appendText(conferenceId, user.uid(), text, now);
        changes.conferenceChanged(conferenceId);
        return new SentConferenceMessageResponseDTO(projections.message(new ConferenceStore.MessageRow(
                id, user.uid(), text, null, null, null, 0L, now.toEpochMilli())));
    }

    /** Текст чата: переносы строк остаются, лишние пробелы — нет. */
    static final class ConferenceText {

        private ConferenceText() {
        }

        static String clean(String value) {
            String text = (value == null ? "" : value).replace("\r\n", "\n").replace('\r', '\n')
                    .replaceAll("[ \\t]+", " ").trim();
            return text.length() > ConferenceRules.MAX_TEXT_LENGTH
                    ? text.substring(0, ConferenceRules.MAX_TEXT_LENGTH) : text;
        }
    }
}
