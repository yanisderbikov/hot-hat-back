package ru.hothat.conference.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.config.ApiException;
import ru.hothat.conference.domain.ConferenceRules;
import ru.hothat.conference.domain.ConferenceRules.MemberStatus;
import ru.hothat.conference.store.ConferenceStore;

import java.time.Clock;
import java.util.List;

/**
 * Кто вправе видеть и менять видео-чат.
 *
 * <p>Три вопроса — «есть ли он», «участник ли ты», «хозяин ли ты» — заданы
 * здесь один раз и в этом порядке. Раньше их задавала каждая из тринадцати
 * функций на Vercel по-своему.
 *
 * <p>Ответ несёт и паспорт, и состав: почти каждому сценарию нужны оба, а
 * второе чтение состава ради проверки права было бы чтением того, что
 * вызывающий тут же прочтёт снова.
 */
@Component
@RequiredArgsConstructor
public class ConferenceAccess {

    private final ConferenceStore store;
    private final Clock clock;

    /** Паспорт и состав живого видео-чата. */
    public record Opened(ConferenceStore.ConferenceRow conference, List<ConferenceStore.MemberRow> members) {

        /** Вошедшие: приняли приглашение либо завели видео-чат. */
        public List<ConferenceStore.MemberRow> participants() {
            return members.stream().filter(row -> row.status() == MemberStatus.MEMBER).toList();
        }

        /** Позванные, ещё не ответившие. */
        public List<ConferenceStore.MemberRow> invited() {
            return members.stream().filter(row -> row.status() == MemberStatus.INVITED).toList();
        }

        public boolean isParticipant(String uid) {
            return participants().stream().anyMatch(row -> row.uid().equals(uid));
        }

        public boolean isHost(String uid) {
            return conference.hostUid().equals(uid);
        }
    }

    /**
     * Живой видео-чат по идентификатору.
     *
     * <p>410, а не 404, у истёкшего: видео-чат был, и ссылка на него честная,
     * просто срок вышел — чинить тут нечего, и человеку это стоит сказать.
     */
    public Opened requireOpen(String conferenceId) {
        if (conferenceId == null || !ConferenceRules.ID.matcher(conferenceId).matches()) {
            throw ApiException.of("CONFERENCE_NOT_FOUND", 404);
        }
        ConferenceStore.ConferenceRow conference = store.conference(conferenceId)
                .orElseThrow(() -> ApiException.of("CONFERENCE_NOT_FOUND", 404));
        if (ConferenceRules.expired(conference.closed(), conference.expiresAtMs(), clock.millis())) {
            throw ApiException.of("CONFERENCE_CLOSED", 410);
        }
        return new Opened(conference, store.members(conferenceId));
    }

    /**
     * Видео-чат, в котором этот человек — участник.
     *
     * <p>Один отказ и на «не звали», и на «выгнали», и на «сам вышел»: по
     * коду ответа нельзя узнать, что о тебе записано в чужом видео-чате.
     */
    public Opened requireParticipant(String conferenceId, String uid) {
        Opened opened = requireOpen(conferenceId);
        if (!opened.isParticipant(uid)) {
            throw ApiException.of("CONFERENCE_INVITE_REQUIRED", 403);
        }
        return opened;
    }

    /** Видео-чат, который этот человек ведёт. */
    public Opened requireHost(String conferenceId, String uid) {
        Opened opened = requireParticipant(conferenceId, uid);
        if (!opened.isHost(uid)) {
            throw ApiException.of("CONFERENCE_HOST_ONLY", 403);
        }
        return opened;
    }
}
