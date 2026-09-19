package ru.hothat.realtime.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.conference.api.dto.ConferenceMessagesResponseDTO;
import ru.hothat.conference.usecase.GetConferenceUseCase;
import ru.hothat.conference.usecase.ReadConferenceMessagesUseCase;
import ru.hothat.config.HotHatUser;
import ru.hothat.realtime.api.dto.ConferenceChannelView;

/**
 * Показать видео-чат слушателю канала {@code /ws/v2/conference/{id}}.
 *
 * <p>Сценарий один и тот же для обоих кадров: канал спрашивает одно — «как
 * выглядит видео-чат для этого участника сейчас». Собирают ответ те же два
 * сценария, что отвечают на {@code GET /api/v2/conference/{id}} и
 * {@code GET …/messages}, — вместе с проверкой права на <b>каждом</b>
 * чтении: выгнанный перестаёт получать кадры сразу, а не когда закроет
 * вкладку, и его сокет закрывается отказом {@code CONFERENCE_INVITE_REQUIRED}.
 *
 * <p>Область {@code realtime} зовёт сценарии области {@code conference} — то
 * же отступление от §7.3, что у остальных каналов, и по той же причине: своя
 * сборка была бы второй правдой о том же экране.
 */
@Service
@RequiredArgsConstructor
public class StreamConferenceChannelUseCase {

    private final GetConferenceUseCase getConference;
    private final ReadConferenceMessagesUseCase readMessages;

    /**
     * Своя транзакция на каждое чтение, а не участие в чужой: кадр
     * собирается и из потока рассылки, где транзакция писателя уже
     * зафиксирована, но ещё привязана к потоку, и присоединяться к ней нельзя.
     * Отказ права (выгнанный) откатывает только это чтение.
     */
    @PreAuthorize("hasRole('USER')")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ConferenceChannelView run(HotHatUser user, String conferenceId) {
        ConferenceMessagesResponseDTO messages = readMessages.run(user, conferenceId);
        return new ConferenceChannelView(
                getConference.run(user, conferenceId).conference(),
                messages.items(),
                messages.limit());
    }
}
