package ru.hothat.conference.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.conference.api.dto.ConferenceTurnView;
import ru.hothat.conference.api.dto.ConferenceVideoTokenResponseDTO;
import ru.hothat.conference.port.ConferenceVideoPort;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.spi.PlayerCardPort;

/**
 * Пустить участника в звонок.
 *
 * <p>Единственное решение здесь — «участник ли ты»; подпись токена и учётка
 * ретранслятора живут за {@link ConferenceVideoPort}. Имя под видео —
 * из карточки игрока, а не из тела запроса: раньше страница присылала
 * {@code participant_name} сама, и подписаться чужим именем мог кто угодно.
 */
@Service
@RequiredArgsConstructor
public class IssueConferenceVideoTokenUseCase {

    /** Столько знаков имени помещается на плашке под видео. */
    private static final int MAX_NAME_LENGTH = 40;

    private final ConferenceAccess access;
    private final PlayerCardPort cards;
    private final ConferenceVideoPort video;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public ConferenceVideoTokenResponseDTO run(HotHatUser user, String conferenceId) {
        access.requireParticipant(conferenceId, user.uid());
        String name = cards.nicknameOf(user.uid()).replaceAll("\\s+", " ").trim();
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }
        ConferenceTurnView turn = video.turnTicket(user.uid())
                .map(ticket -> new ConferenceTurnView(ticket.urls(), ticket.username(), ticket.credential(),
                        ticket.ttlSeconds()))
                .orElse(null);
        return new ConferenceVideoTokenResponseDTO(
                video.serverUrl(),
                video.participantToken(conferenceId, user.uid(), name),
                user.uid(),
                turn);
    }
}
