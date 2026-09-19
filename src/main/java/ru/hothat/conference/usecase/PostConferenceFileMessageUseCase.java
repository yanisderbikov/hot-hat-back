package ru.hothat.conference.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.conference.api.dto.PostConferenceFileMessageRequestDTO;
import ru.hothat.conference.api.dto.SentConferenceMessageResponseDTO;
import ru.hothat.conference.domain.ConferenceFileKey;
import ru.hothat.conference.store.ConferenceStore;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.realtime.spi.RealtimeChangeBus;

import java.time.Clock;
import java.time.Instant;

/**
 * Приложить к чату файл, уже загруженный по билету.
 *
 * <p>Владение читается из ключа: файл обязан лежать в папке этого
 * видео-чата и этого отправителя. Чужой ключ или ключ другого созвона
 * отвергается до записи — иначе в ленту можно было бы подсунуть чужое
 * вложение под своим именем.
 */
@Service
@RequiredArgsConstructor
public class PostConferenceFileMessageUseCase {

    private final ConferenceAccess access;
    private final ConferenceStore store;
    private final ConferenceProjections projections;
    private final RealtimeChangeBus changes;
    private final Clock clock;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public SentConferenceMessageResponseDTO run(HotHatUser user, String conferenceId,
                                                PostConferenceFileMessageRequestDTO request) {
        access.requireParticipant(conferenceId, user.uid());
        if (!ConferenceFileKey.belongsTo(request.storageKey(), conferenceId, user.uid())) {
            throw ApiException.of("CONFERENCE_FILE_INVALID", 403);
        }
        String text = PostConferenceMessageUseCase.ConferenceText.clean(request.text());
        String name = ConferenceFileKey.safeName(request.name());
        String mime = request.contentType().split(";")[0].trim().toLowerCase();
        Instant now = Instant.now(clock);
        long id = store.appendFile(conferenceId, user.uid(), text.isEmpty() ? null : text,
                request.storageKey(), name, mime, request.sizeBytes(), now);
        changes.conferenceChanged(conferenceId);
        return new SentConferenceMessageResponseDTO(projections.message(new ConferenceStore.MessageRow(
                id, user.uid(), text.isEmpty() ? null : text, request.storageKey(), name, mime,
                request.sizeBytes(), now.toEpochMilli())));
    }
}
