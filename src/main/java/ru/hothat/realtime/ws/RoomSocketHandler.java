package ru.hothat.realtime.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.WebSocketSession;
import ru.hothat.config.ApiException;
import ru.hothat.realtime.api.dto.RoomChannelEventDTO;
import ru.hothat.realtime.api.dto.RoomChannelHelloDTO;
import ru.hothat.realtime.spi.RealtimeEvents;
import ru.hothat.realtime.usecase.StreamRoomChannelUseCase;
import ru.hothat.util.Ids;

/**
 * Канал игрового экрана — {@code /ws/v2/room/{roomId}}.
 *
 * <p>Заменяет шесть живых подписок сразу: документ комнаты, места, составы,
 * зрители, чат и свои слова ({@code app-core.js:8704}, {@code :8820},
 * {@code :8831}, {@code :8858}, {@code :8865}, {@code :8876}). Вместе с ними
 * исчезает и главная беда прежнего устройства: браузер получал документ
 * комнаты целиком — с мешком неразыгранных слов и словом, которое объясняет
 * чужая команда, — а не показывал их по собственной доброй воле.
 *
 * <p>Комната стоит в адресе, а не в кадре подписки: у сокетов нет переменных
 * пути, поэтому последний сегмент разбирает сам обработчик. Идентификатор
 * проверяется тем же выражением, что и {@code @RoomId} на адресах HTTP.
 *
 * <p>Право — место в комнате, игрока либо зрителя, — проверяет сценарий, и
 * проверяет до того, как сокет попадёт в реестр подписок.
 */
@Component
public class RoomSocketHandler extends ChannelSocketHandler {

    private final StreamRoomChannelUseCase streamRoom;

    public RoomSocketHandler(ObjectMapper mapper, StreamRoomChannelUseCase streamRoom) {
        super(mapper);
        this.streamRoom = streamRoom;
    }

    @Override
    protected String key(WebSocketSession session) {
        String roomId = lastSegment(session.getUri()).trim().toLowerCase();
        if (!Ids.ROOM.matcher(roomId).matches()) {
            // Тот же ответ, что у адреса HTTP с непохожим идентификатором:
            // «комнаты нет». Отдельный код «адрес кривой» сказал бы клиенту
            // то, что он и так знает, и добавил бы ветку разбора.
            throw ApiException.of("ROOM_NOT_FOUND", 404);
        }
        return roomId;
    }

    @Override
    protected Object hello(Subscriber subscriber) {
        return new RoomChannelHelloDTO(underPrincipal(subscriber,
                () -> streamRoom.run(subscriber.user(), subscriber.key())));
    }

    @Override
    protected Object event(Subscriber subscriber) {
        return new RoomChannelEventDTO(underPrincipal(subscriber,
                () -> streamRoom.run(subscriber.user(), subscriber.key())));
    }

    /**
     * В комнате что-то изменилось — рассылаем её участникам новый снимок.
     *
     * <p>{@code AFTER_COMMIT}, а не обычный {@code @EventListener}: до фиксации
     * транзакции стол показал бы отгаданное слово, которого при откате не
     * останется, а рассылка была бы частью запроса на запись — любая ошибка
     * внутри неё роняла бы само действие игрока.
     *
     * <p>{@code REQUIRES_NEW} нужен вместе с {@code AFTER_COMMIT}: транзакция
     * писателя уже зафиксирована, но ещё связана с потоком, и вложенное чтение
     * присоединилось бы к завершённой. Отдельная транзакция читает
     * зафиксированное состояние и ничему уже не может помешать.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onRoomChanged(RealtimeEvents.RoomChanged event) {
        broadcast(event.roomId());
    }
}
