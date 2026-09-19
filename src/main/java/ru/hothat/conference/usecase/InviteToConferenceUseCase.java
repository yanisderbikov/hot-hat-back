package ru.hothat.conference.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.conference.api.dto.ConferenceInviteOutcome;
import ru.hothat.conference.api.dto.InviteToConferenceRequestDTO;
import ru.hothat.conference.api.dto.SentConferenceInviteResponseDTO;
import ru.hothat.conference.domain.ConferenceRules;
import ru.hothat.conference.port.ConferenceVideoPort;
import ru.hothat.conference.store.ConferenceStore;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.friend.spi.FriendshipPort;
import ru.hothat.realtime.spi.RealtimeChangeBus;

import java.time.Clock;
import java.time.Instant;

/**
 * Позвать друга в видео-чат.
 *
 * <p>Звать может любой участник, не только хозяин: созвон собирают вместе.
 * Звать можно только друга — тот же порог, что у личной переписки, и тот же
 * порт дружбы отвечает на вопрос.
 *
 * <p>Уже вошедшего можно позвать снова, если в звонке его нет: закрывший
 * вкладку остаётся в составе, и без этого его нельзя было бы вернуть иначе
 * как выгнав. Есть ли он в звонке, спрашивают у видеоузла — единственного,
 * кто это знает; раньше это знал только экран хозяина.
 *
 * <p>Приглашение — состояние строки участия, поэтому у одного человека
 * второго приглашения не бывает: повтор отвечает {@code ALREADY_PENDING}
 * и ничего не пишет.
 */
@Service
@RequiredArgsConstructor
public class InviteToConferenceUseCase {

    private final ConferenceAccess access;
    private final ConferenceStore store;
    private final ConferenceProjections projections;
    private final FriendshipPort friendships;
    private final ConferenceVideoPort video;
    private final RealtimeChangeBus changes;
    private final Clock clock;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public SentConferenceInviteResponseDTO run(HotHatUser user, String conferenceId,
                                               InviteToConferenceRequestDTO request) {
        ConferenceAccess.Opened opened = access.requireParticipant(conferenceId, user.uid());
        String friendUid = request.friendUid();
        if (friendUid.equals(user.uid())) {
            throw ApiException.of("CONFERENCE_SELF_INVITE", 400);
        }
        if (!friendships.areFriends(user.uid(), friendUid)) {
            throw ApiException.of("FRIEND_REQUIRED", 403);
        }

        ConferenceStore.MemberRow existing = opened.members().stream()
                .filter(row -> row.uid().equals(friendUid)).findFirst().orElse(null);
        ConferenceRules.MemberStatus status = existing == null ? null : existing.status();

        if (status == ConferenceRules.MemberStatus.INVITED) {
            return new SentConferenceInviteResponseDTO(friendUid, ConferenceInviteOutcome.ALREADY_PENDING,
                    projections.view(opened));
        }
        if (status == ConferenceRules.MemberStatus.MEMBER) {
            if (video.liveIdentities(conferenceId).contains(friendUid)) {
                return new SentConferenceInviteResponseDTO(friendUid, ConferenceInviteOutcome.ALREADY_MEMBER,
                        projections.view(opened));
            }
            // Ушёл, не выходя: место его больше не держит, и на предел он не
            // давит — считать его среди вошедших нечестно.
        } else if (!ConferenceRules.hasRoomFor(opened.participants().size(), opened.invited().size())) {
            throw ApiException.of("CONFERENCE_FULL", 409);
        }

        store.invite(conferenceId, friendUid, user.uid(), Instant.now(clock));
        changes.conferenceChanged(conferenceId);
        // Карточка «вас зовут» живёт в кадре /ws/v2/me/social адресата.
        changes.socialChanged(friendUid);
        return new SentConferenceInviteResponseDTO(friendUid, ConferenceInviteOutcome.INVITED,
                projections.view(access.requireParticipant(conferenceId, user.uid())));
    }
}
