package ru.hothat.realtime.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.WebSocketSession;
import ru.hothat.realtime.api.dto.MemeLibraryEventDTO;
import ru.hothat.realtime.api.dto.MemeLibraryHelloDTO;
import ru.hothat.realtime.spi.RealtimeEvents;
import ru.hothat.realtime.usecase.StreamMemeLibraryUseCase;

/**
 * Канал библиотеки мемов — {@code /ws/v2/media/memes}.
 *
 * <p>Заменяет живую подписку на коллекцию {@code memeLibrary}
 * ({@code app-core.js:6037}). Библиотека одна на всех, поэтому ключ рассылки
 * постоянный; но снимок собирается на каждого слушателя отдельно — в карточке
 * есть признак «мой», от которого зависит кнопка удаления.
 *
 * <p>Библиотека меняется редко — мем выкладывают и снимают руками, — поэтому
 * склейки рассылок здесь нет: она усложнила бы канал ради события, которого в
 * обычный час не случается вовсе.
 */
@Component
public class MemeLibrarySocketHandler extends ChannelSocketHandler {

    /** Библиотека одна на всех: ключ рассылки постоянный. */
    private static final String LIBRARY = "memeLibrary";

    private final StreamMemeLibraryUseCase streamLibrary;

    public MemeLibrarySocketHandler(ObjectMapper mapper, StreamMemeLibraryUseCase streamLibrary) {
        super(mapper);
        this.streamLibrary = streamLibrary;
    }

    @Override
    protected String key(WebSocketSession session) {
        return LIBRARY;
    }

    @Override
    protected Object hello(Subscriber subscriber) {
        return new MemeLibraryHelloDTO(underPrincipal(subscriber, () -> streamLibrary.run(subscriber.user())));
    }

    @Override
    protected Object event(Subscriber subscriber) {
        return new MemeLibraryEventDTO(underPrincipal(subscriber, () -> streamLibrary.run(subscriber.user())));
    }

    /**
     * Библиотека изменилась — шлём всем её слушателям новый снимок.
     *
     * <p>{@code AFTER_COMMIT}: до фиксации в чужой сетке появился бы мем,
     * которого при откате не станет, — а его ещё и заряжают в обойму.
     * {@code REQUIRES_NEW} — транзакция писателя уже зафиксирована, но ещё
     * связана с потоком.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onMemeLibraryChanged(RealtimeEvents.MemeLibraryChanged event) {
        broadcast(LIBRARY);
    }
}
