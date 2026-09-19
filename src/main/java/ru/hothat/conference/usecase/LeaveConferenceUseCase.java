package ru.hothat.conference.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.conference.domain.ConferenceRules.MemberStatus;
import ru.hothat.conference.store.ConferenceStore;
import ru.hothat.config.HotHatUser;
import ru.hothat.realtime.spi.RealtimeChangeBus;

import java.time.Clock;
import java.time.Instant;

/**
 * Выйти из видео-чата.
 *
 * <p>Хозяин не выходит: видео-чат живёт до срока, и хозяин вправе вернуться
 * по ссылке. Для него это отключение от звонка без записи — состав не
 * меняется, и рассылать нечего.
 *
 * <p>Не участнику отвечать нечем и незачем: выход — идемпотентная просьба,
 * и второй выход подряд так же успешен, как первый.
 */
@Service
@RequiredArgsConstructor
public class LeaveConferenceUseCase {

    private final ConferenceAccess access;
    private final ConferenceStore store;
    private final RealtimeChangeBus changes;
    private final Clock clock;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void run(HotHatUser user, String conferenceId) {
        ConferenceAccess.Opened opened = access.requireOpen(conferenceId);
        if (opened.isHost(user.uid()) || !opened.isParticipant(user.uid())) {
            return;
        }
        store.setStatus(conferenceId, user.uid(), MemberStatus.LEFT, Instant.now(clock));
        changes.conferenceChanged(conferenceId);
    }
}
