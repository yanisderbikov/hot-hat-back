package ru.hothat.conference.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.conference.api.dto.ConferenceUploadTicketResponseDTO;
import ru.hothat.conference.api.dto.RequestConferenceUploadTicketRequestDTO;
import ru.hothat.conference.domain.ConferenceFileKey;
import ru.hothat.conference.domain.ConferenceRules;
import ru.hothat.conference.store.ConferenceFileStorage;
import ru.hothat.config.HotHatUser;
import ru.hothat.util.Ids;

import java.time.Clock;

/**
 * Выдать билет на загрузку файла в чат.
 *
 * <p>Байты идут мимо бекенда: браузер кладёт файл по подписанной ссылке, а
 * потом называет ключ в сообщении. Ключ строит сервер — в нём видео-чат,
 * отправитель и случайный хвост, — и по нему же потом читается владение.
 */
@Service
@RequiredArgsConstructor
public class IssueConferenceUploadTicketUseCase {

    private final ConferenceAccess access;
    private final ConferenceFileStorage files;
    private final Clock clock;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public ConferenceUploadTicketResponseDTO run(HotHatUser user, String conferenceId,
                                                 RequestConferenceUploadTicketRequestDTO request) {
        access.requireParticipant(conferenceId, user.uid());
        files.requireConfigured();
        long now = clock.millis();
        String name = ConferenceFileKey.safeName(request.name());
        String key = ConferenceFileKey.of(conferenceId, user.uid(), now, Ids.hex(6), name);
        return new ConferenceUploadTicketResponseDTO(
                files.presignUpload(key, request.contentType()),
                request.contentType(),
                key,
                name,
                now + ConferenceFileStorage.UPLOAD_TTL.toMillis(),
                ConferenceRules.MAX_FILE_BYTES);
    }
}
