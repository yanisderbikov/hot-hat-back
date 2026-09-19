package ru.hothat.conference.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.conference.api.dto.AnsweredConferenceInviteResponseDTO;
import ru.hothat.conference.domain.ConferenceRules;
import ru.hothat.conference.domain.ConferenceRules.MemberStatus;
import ru.hothat.conference.store.ConferenceStore;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.realtime.spi.RealtimeChangeBus;

import java.time.Clock;
import java.time.Instant;

/**
 * Ответить на приглашение: вступить или отклонить.
 *
 * <p>Ответить может только адресат, и только на ждущее приглашение:
 * повторный ответ ничего не переставляет. Отказ один и на чужое, и на
 * несуществующее: по коду ответа нельзя узнать, звали ли тебя.
 *
 * <p>Вместимость проверяется при вступлении ещё раз: приглашённых считали
 * вместе с вошедшими, но между приглашением и ответом хозяин мог позвать
 * ушедшего обратно.
 */
@Service
@RequiredArgsConstructor
public class AnswerConferenceInviteUseCase {

    private final ConferenceAccess access;
    private final ConferenceStore store;
    private final ConferenceProjections projections;
    private final RealtimeChangeBus changes;
    private final Clock clock;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public AnsweredConferenceInviteResponseDTO run(HotHatUser user, String conferenceId, boolean accept) {
        ConferenceAccess.Opened opened = access.requireOpen(conferenceId);
        ConferenceStore.MemberRow mine = opened.members().stream()
                .filter(row -> row.uid().equals(user.uid())).findFirst().orElse(null);
        if (mine == null || mine.status() != MemberStatus.INVITED) {
            throw ApiException.of("CONFERENCE_INVITE_NOT_FOUND", 404);
        }
        if (accept && opened.participants().size() >= ConferenceRules.MAX_PARTICIPANTS) {
            throw ApiException.of("CONFERENCE_FULL", 409);
        }
        store.setStatus(conferenceId, user.uid(), accept ? MemberStatus.MEMBER : MemberStatus.DECLINED,
                Instant.now(clock));
        changes.conferenceChanged(conferenceId);
        changes.socialChanged(user.uid());
        return new AnsweredConferenceInviteResponseDTO(conferenceId, accept,
                accept ? projections.view(access.requireParticipant(conferenceId, user.uid())) : null);
    }
}
