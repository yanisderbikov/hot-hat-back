package ru.hothat.realtime.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.WebSocketSession;
import ru.hothat.config.ApiException;
import ru.hothat.conference.domain.ConferenceRules;
import ru.hothat.realtime.api.dto.ConferenceChannelEventDTO;
import ru.hothat.realtime.api.dto.ConferenceChannelHelloDTO;
import ru.hothat.realtime.spi.RealtimeEvents;
import ru.hothat.realtime.usecase.StreamConferenceChannelUseCase;

/**
 * Канал одного видео-чата — {@code /ws/v2/conference/{conferenceId}}.
 *
 * <p>Заменяет два опроса, которыми жила страница в ветке dev: ленту раз в
 * пять секунд и состав раз в восемь. Здесь участник получает состав, ленту
 * и заведённую комнату одним кадром — и ровно тогда, когда они изменились.
 *
 * <p>Ключ рассылки — идентификатор видео-чата: он и есть предмет канала, и
 * им же названо событие {@link RealtimeEvents.ConferenceChanged}.
 *
 * <p>Транспорт тонкий: разобрать адрес, спросить <b>один</b> сценарий,
 * отправить кадр. Право проверяет сценарий на каждом чтении, поэтому
 * выгнанный получает отказ и закрытие сокета, а не следующий кадр.
 */
@Component
public class ConferenceSocketHandler extends ChannelSocketHandler {

    private final StreamConferenceChannelUseCase streamConference;

    public ConferenceSocketHandler(ObjectMapper mapper, StreamConferenceChannelUseCase streamConference) {
        super(mapper);
        this.streamConference = streamConference;
    }

    @Override
    protected String key(WebSocketSession session) {
        String conferenceId = lastSegment(session.getUri());
        if (!ConferenceRules.ID.matcher(conferenceId).matches()) {
            throw ApiException.of("CONFERENCE_NOT_FOUND", 404);
        }
        return conferenceId;
    }

    @Override
    protected Object hello(Subscriber subscriber) {
        return new ConferenceChannelHelloDTO(underPrincipal(subscriber,
                () -> streamConference.run(subscriber.user(), conferenceId(subscriber))));
    }

    @Override
    protected Object event(Subscriber subscriber) {
        return new ConferenceChannelEventDTO(underPrincipal(subscriber,
                () -> streamConference.run(subscriber.user(), conferenceId(subscriber))));
    }

    /**
     * Видео-чат изменился — рассылаем участникам новый снимок.
     *
     * <p>{@code AFTER_COMMIT} — по той же причине, что у канала переписки: до
     * фиксации участник увидел бы то, чего при откате не останется. Своей
     * транзакции на всю рассылку здесь нет: её открывает сценарий кадра —
     * отдельную на каждого слушателя ({@code REQUIRES_NEW} в
     * {@link StreamConferenceChannelUseCase}). Рассылка после выгона обязана
     * закончиться отказом ровно одному сокету, а общая транзакция на всех
     * помечалась бы этим отказом как откатываемая и падала на фиксации
     * уже после того, как остальные получили кадр.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onConferenceChanged(RealtimeEvents.ConferenceChanged event) {
        broadcast(event.conferenceId());
    }

    /** Видео-чат из адреса; вид уже проверен при выдаче ключа. */
    private static String conferenceId(Subscriber subscriber) {
        return lastSegment(subscriber.session().getUri());
    }
}
