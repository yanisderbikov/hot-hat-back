package ru.hothat.realtime.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.WebSocketSession;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.realtime.api.dto.DirectChatEventDTO;
import ru.hothat.realtime.api.dto.DirectChatHelloDTO;
import ru.hothat.realtime.usecase.StreamDirectChatUseCase;
import ru.hothat.chat.spi.DirectChatChangedEvent;
import ru.hothat.util.Ids;

import java.util.regex.Pattern;

/**
 * Канал одной личной переписки — {@code /ws/v2/chat/{peerUid}}.
 *
 * <p>Первый именованный канал вместо универсального шлюза «путь строкой».
 * Прежняя подписка вела на сырые документы {@code directChats/{pair}/messages}
 * и присылала строку таблицы ({@code realtime-social.js:99},
 * {@code portal.js:133}, {@code friends/friends.js:31}); здесь приезжает та же
 * проекция, что у {@code GET /api/v2/chat/{peerUid}/messages}, и у интерфейса
 * остаётся одна ветка отрисовки вместо двух.
 *
 * <p>Пару собеседников считает сервер: клиент называет только собеседника,
 * а по какому ключу лежит переписка — его дело не касается. Она же и ключ
 * рассылки: участников у пары двое, поэтому в рассылке нет веера — цикл идёт
 * по двум вкладкам, а не по коллекции неизвестного размера.
 *
 * <p>Транспорт здесь тонкий, как контроллер: разобрать адрес, спросить
 * <b>один</b> сценарий, отправить кадр. Всё остальное — рукопожатие,
 * сердцебиение, {@code unsubscribe}, порядок «право раньше регистрации» и
 * возврат чужого контекста безопасности — общее у семи каналов и живёт в
 * {@link ChannelSocketHandler}.
 */
@Component
public class DirectChatSocketHandler extends ChannelSocketHandler {

    /** Тот же вид идентификатора, что проверяет {@code @PlayerUid} на HTTP-адресах. */
    private static final Pattern PEER_UID = Pattern.compile("^[A-Za-z0-9_-]{1,160}$");

    private final StreamDirectChatUseCase streamDirectChat;

    public DirectChatSocketHandler(ObjectMapper mapper, StreamDirectChatUseCase streamDirectChat) {
        super(mapper);
        this.streamDirectChat = streamDirectChat;
    }

    /**
     * Ключ рассылки — пара собеседников, ровно та же строка, которой называет
     * переписку событие о новом сообщении. Считается она из личности владельца
     * сокета и собеседника из адреса, а не из ответа сценария: ключ нужен
     * раньше первого чтения — по нему сокет попадает в реестр.
     */
    @Override
    protected String key(WebSocketSession session) {
        String peerUid = lastSegment(session.getUri());
        if (!PEER_UID.matcher(peerUid).matches()) {
            throw ApiException.of("PLAYER_NOT_FOUND", 404);
        }
        Object user = session.getAttributes().get(ChannelHandshakeAuthenticator.USER_ATTRIBUTE);
        if (!(user instanceof HotHatUser known)) {
            throw ApiException.of("AUTH_REQUIRED", 401);
        }
        return Ids.pair(known.uid(), peerUid);
    }

    @Override
    protected Object hello(Subscriber subscriber) {
        return new DirectChatHelloDTO(underPrincipal(subscriber,
                () -> streamDirectChat.run(subscriber.user(), peerUid(subscriber))));
    }

    @Override
    protected Object event(Subscriber subscriber) {
        return new DirectChatEventDTO(underPrincipal(subscriber,
                () -> streamDirectChat.run(subscriber.user(), peerUid(subscriber))));
    }

    /**
     * В переписке появилось сообщение — рассылаем её участникам новый снимок.
     *
     * <p>{@code AFTER_COMMIT}, а не обычный {@code @EventListener}: до фиксации
     * транзакции отправителя собеседник увидел бы сообщение, которого при
     * откате не останется, а рассылка была бы частью запроса на запись —
     * любая ошибка внутри неё роняла бы саму отправку.
     *
     * <p>{@code REQUIRES_NEW} нужен вместе с {@code AFTER_COMMIT}: в этот
     * момент транзакция уже зафиксирована, но ещё связана с потоком, и
     * вложенное чтение присоединилось бы к завершённой. Отдельная транзакция
     * читает зафиксированное состояние и ничему уже не может помешать.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDirectChatChanged(DirectChatChangedEvent event) {
        broadcast(event.pair());
    }

    /** Собеседник из адреса; вид уже проверен при выдаче ключа. */
    private String peerUid(Subscriber subscriber) {
        return lastSegment(subscriber.session().getUri());
    }
}
