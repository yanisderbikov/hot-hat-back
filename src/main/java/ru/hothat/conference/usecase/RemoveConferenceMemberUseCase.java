package ru.hothat.conference.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.conference.api.dto.ConferenceResponseDTO;
import ru.hothat.conference.domain.ConferenceRules.MemberStatus;
import ru.hothat.conference.port.ConferenceVideoPort;
import ru.hothat.conference.store.ConferenceStore;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.realtime.spi.RealtimeChangeBus;

import java.time.Clock;
import java.time.Instant;

/**
 * Выгнать участника из видео-чата.
 *
 * <p>Право хозяина. Себя хозяин выгнать не может — у видео-чата тогда не
 * осталось бы ведущего. Выгнанного видеоузел отключает сразу, а следующий
 * токен ему уже не выдадут: состав — источник правды, звонок — следствие.
 *
 * <p>Отзыв приглашения — тот же адрес: позванный, но не ответивший, тоже
 * строка состава, и убрать её значит снять карточку у него с экрана.
 */
@Service
@RequiredArgsConstructor
public class RemoveConferenceMemberUseCase {

    private final ConferenceAccess access;
    private final ConferenceStore store;
    private final ConferenceProjections projections;
    private final ConferenceVideoPort video;
    private final RealtimeChangeBus changes;
    private final Clock clock;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public ConferenceResponseDTO run(HotHatUser user, String conferenceId, String targetUid) {
        ConferenceAccess.Opened opened = access.requireHost(conferenceId, user.uid());
        if (targetUid.equals(user.uid())) {
            throw ApiException.of("CONFERENCE_HOST_PROTECTED", 409);
        }
        ConferenceStore.MemberRow target = opened.members().stream()
                .filter(row -> row.uid().equals(targetUid)).findFirst().orElse(null);
        if (target == null || (target.status() != MemberStatus.MEMBER && target.status() != MemberStatus.INVITED)) {
            throw ApiException.of("CONFERENCE_PARTICIPANT_NOT_FOUND", 404);
        }
        store.setStatus(conferenceId, targetUid, MemberStatus.REMOVED, Instant.now(clock));
        if (target.status() == MemberStatus.MEMBER) {
            video.disconnect(conferenceId, targetUid);
        }
        changes.conferenceChanged(conferenceId);
        changes.socialChanged(targetUid);
        return new ConferenceResponseDTO(projections.view(access.requireHost(conferenceId, user.uid())));
    }
}
