package ru.hothat.realtime.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import ru.hothat.config.ApiException;
import ru.hothat.config.ErrorMessages;
import ru.hothat.config.GlobalExceptionHandler;
import ru.hothat.config.HotHatUser;
import ru.hothat.realtime.api.dto.ChannelErrorFrameDTO;
import ru.hothat.realtime.api.dto.ChannelPongFrameDTO;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Общее устройство именованного канала {@code /ws/v2/**}.
 *
 * <p>Вынесено в предка не ради экономии строк, а ради одинаковости. Каналов
 * семь, и у всех семи один и тот же порядок действий, в котором ошибиться
 * легко и дорого:
 *
 * <ol>
 *   <li>личность берётся из рукопожатия — в потоке сокета контекста
 *       безопасности не создаёт никакой фильтр;</li>
 *   <li>предмет канала разбирается из адреса — переменных пути у сокетов нет;</li>
 *   <li><b>право проверяется до регистрации подписки</b>, а не после. У
 *       прежнего документного шлюза было наоборот, и подписка на запрещённый
 *       путь жила там вечно, отравляя рассылку остальным (находка B1 аудита);</li>
 *   <li>и только потом сокет попадает в реестр и получает приветственный кадр.</li>
 * </ol>
 *
 * <p>Контекст безопасности выставляется вручную на каждое чтение и снимается
 * после. В потоке рассылки он вообще чужой — там доигрывается транзакция
 * <i>писателя</i>, — и оставить в нём права слушателя значило бы подменить
 * личность посреди чужого запроса.
 *
 * <p>Право перепроверяется на каждом кадре, а не однажды при подписке. Это
 * дороже, но закрывает дыру: выгнанный из комнаты и удалённый из друзей
 * перестают получать кадры сразу, а не когда закроют вкладку.
 */
@Slf4j
public abstract class ChannelSocketHandler extends TextWebSocketHandler {

    /** Ключ рассылки, под которым сокет зарегистрирован; нужен при закрытии. */
    private static final String KEY_ATTRIBUTE = "channelKey";

    protected final ObjectMapper mapper;

    /**
     * Ключ рассылки → открытые сокеты (идентификатор сессии → подписчик).
     *
     * <p>Ключ — предмет канала: комната, команда, игрок, а у общих каналов —
     * их собственное имя. Рассылка идёт по одному ключу и не перебирает
     * соединения, которых событие не касается.
     */
    private final Map<String, Map<String, Subscriber>> channels = new ConcurrentHashMap<>();

    /**
     * Что известно о сокете.
     *
     * @param user личность игрока; {@code null} у машинного канала, где за
     *             подписчиком нет строки в {@code app_user}
     */
    protected record Subscriber(WebSocketSession session, HotHatUser user,
                                Authentication authentication, String key) {
    }

    protected ChannelSocketHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Предмет канала из адреса — он же ключ рассылки.
     *
     * <p>Отказ выражается {@link ApiException} с кодом области: «комнаты нет»
     * и «игрока нет» — разные ответы, и придумывать здесь общий значило бы
     * врать одному из них.
     */
    protected abstract String key(WebSocketSession session);

    /** Приветственный кадр: канал открыт, вот состояние. */
    protected abstract Object hello(Subscriber subscriber);

    /** Кадр обновления: состояние изменилось. */
    protected abstract Object event(Subscriber subscriber);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Object credentials = session.getAttributes().get(ChannelHandshakeAuthenticator.AUTHENTICATION_ATTRIBUTE);
        if (!(credentials instanceof Authentication principal)) {
            refuse(session, "AUTH_REQUIRED", 401);
            return;
        }
        HotHatUser user = session.getAttributes().get(ChannelHandshakeAuthenticator.USER_ATTRIBUTE)
                instanceof HotHatUser known ? known : null;
        String key;
        Object hello;
        Subscriber subscriber;
        try {
            key = key(session);
            subscriber = new Subscriber(session, user, principal, key);
            // Первое чтение стоит до регистрации: оно же и есть проверка права.
            hello = hello(subscriber);
        } catch (ApiException e) {
            refuse(session, e.getCode(), e.getStatus());
            return;
        } catch (AccessDeniedException e) {
            // Отказ @PreAuthorize на сценарии канала. Код тот же, что вернул бы
            // HTTP: у клиента не должно быть второй ветки разбора отказов.
            refuse(session, GlobalExceptionHandler.deniedCode(user), 403);
            return;
        } catch (RuntimeException e) {
            log.warn("Канал {} не открылся: {}", getClass().getSimpleName(), e.toString());
            refuse(session, "CHANNEL_UNAVAILABLE", 500);
            return;
        }
        session.getAttributes().put(KEY_ATTRIBUTE, key);
        // Регистрация целиком внутри compute: иначе последний закрывающийся
        // сокет ключа успевал бы выбросить карту, в которую в этот же миг
        // добавляется новый, — и новый не получал бы ни одного кадра.
        channels.compute(key, (ignored, sockets) -> {
            Map<String, Subscriber> registered = sockets == null ? new ConcurrentHashMap<>() : sockets;
            registered.put(session.getId(), subscriber);
            return registered;
        });
        send(session, hello);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        forget(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        JsonNode frame;
        try {
            frame = mapper.readTree(message.getPayload());
        } catch (IOException e) {
            send(session, new ChannelErrorFrameDTO(ErrorMessages.resolve("BAD_MESSAGE", 400), "BAD_MESSAGE"));
            return;
        }
        String type = frame.path("type").asText();
        switch (type) {
            case "ping" -> send(session, new ChannelPongFrameDTO());
            // Ответного кадра нет намеренно: клиент попросил перестать
            // присылать ему кадры этой подписки.
            case "unsubscribe" -> unsubscribe(session, frame.path("id").asText(""));
            default -> {
                if (!onFrame(session, type, frame)) {
                    send(session, new ChannelErrorFrameDTO(
                            ErrorMessages.resolve("UNKNOWN_FRAME", 400), "UNKNOWN_FRAME"));
                }
            }
        }
    }

    /**
     * Кадр сверх общих трёх.
     *
     * @return разобрал ли канал этот кадр; {@code false} — отвечаем «неизвестный кадр»
     */
    protected boolean onFrame(WebSocketSession session, String type, JsonNode frame) {
        return false;
    }

    /**
     * Снять подписку по имени. По умолчанию у канала предмет один, и снять
     * его — значит перестать получать кадры вовсе; соединение при этом живо,
     * сердцебиение по нему ходит, и клиент волен закрыть его сам.
     */
    protected void unsubscribe(WebSocketSession session, String id) {
        forget(session);
    }

    /**
     * Разослать обновление подписчикам одного ключа.
     *
     * <p>Зовётся из слушателя, помеченного {@code AFTER_COMMIT}: до фиксации
     * подписчики увидели бы состояние, которого при откате не станет.
     */
    protected void broadcast(String key) {
        Map<String, Subscriber> subscribers = channels.get(key);
        if (subscribers == null) {
            return;
        }
        for (Subscriber subscriber : subscribers.values()) {
            deliver(subscriber);
        }
    }

    /** Обновление одному подписчику — там, где кадр строится не всем сразу. */
    protected void deliver(Subscriber subscriber) {
        if (!subscriber.session().isOpen()) {
            return;
        }
        try {
            send(subscriber.session(), event(subscriber));
        } catch (ApiException e) {
            // Право пропало, пока канал был открыт: выгнали из комнаты,
            // удалили из друзей, распустили команду. Канал закрывается сразу,
            // а не доживает до перезагрузки страницы.
            refuse(subscriber.session(), e.getCode(), e.getStatus());
        } catch (AccessDeniedException e) {
            // То же самое, но отказала роль, а не предметная проверка: игрока
            // забанили или разжаловали посреди открытого канала.
            refuse(subscriber.session(), GlobalExceptionHandler.deniedCode(subscriber.user()), 403);
        } catch (RuntimeException e) {
            log.debug("Кадр канала {} не отправлен: {}", getClass().getSimpleName(), e.toString());
        }
    }

    /** Подписчики одного ключа — для каналов со своей формой рассылки. */
    protected Collection<Subscriber> subscribers(String key) {
        Map<String, Subscriber> registered = channels.get(key);
        return registered == null ? List.of() : registered.values();
    }

    /**
     * Подписчик этого сокета — для кадров, отвечающих одному спросившему.
     *
     * <p>Поиск идёт по ключу и номеру сессии, а не перебором открытых
     * соединений: на главной их столько, сколько посетителей, а кадр
     * {@code spotlight} приходит от каждого раз в тридцать секунд.
     *
     * @return {@code null}, если сокет уже снял подписку или ещё не встал в реестр
     */
    protected Subscriber subscriber(WebSocketSession session) {
        Object key = session.getAttributes().get(KEY_ATTRIBUTE);
        if (key == null) {
            return null;
        }
        Map<String, Subscriber> registered = channels.get(String.valueOf(key));
        return registered == null ? null : registered.get(session.getId());
    }

    /**
     * Прочитать что-либо под личностью владельца сокета.
     *
     * <p>Прежний контекст возвращается на место обязательно: в рассылке поток
     * принадлежит писателю, и чужие права в нём — это подмена личности посреди
     * чужого запроса.
     */
    protected <T> T underPrincipal(Subscriber subscriber, Supplier<T> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        try {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(subscriber.authentication());
            SecurityContextHolder.setContext(context);
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    /** Последний сегмент адреса — там, где предмет канала стоит в конце пути. */
    protected static String lastSegment(URI uri) {
        if (uri == null) {
            return "";
        }
        String path = uri.getPath();
        return URLDecoder.decode(path.substring(path.lastIndexOf('/') + 1), StandardCharsets.UTF_8);
    }

    /** Сегмент адреса перед названным хвостом: {@code …/rooms/{roomId}/preflight}. */
    protected static String segmentBefore(URI uri, String tail) {
        if (uri == null) {
            return "";
        }
        String[] segments = uri.getPath().split("/");
        for (int i = segments.length - 1; i > 0; i--) {
            if (segments[i].equals(tail)) {
                return URLDecoder.decode(segments[i - 1], StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    /** Параметр строки запроса: токен, подпись, номер партии. */
    protected static String queryParam(URI uri, String name) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String pair : uri.getQuery().split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /**
     * Отказ: кадр с тем же кодом и текстом, что вернул бы HTTP, и закрытие.
     * Текст произвольного исключения наружу не уходит — только известный код
     * из общего словаря.
     */
    protected void refuse(WebSocketSession session, String code, int status) {
        send(session, new ChannelErrorFrameDTO(ErrorMessages.resolve(code, status), ErrorMessages.publicCode(code)));
        forget(session);
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException e) {
            log.debug("Канал уже закрыт: {}", e.getMessage());
        }
    }

    protected void forget(WebSocketSession session) {
        Object key = session.getAttributes().get(KEY_ATTRIBUTE);
        if (key == null) {
            return;
        }
        channels.computeIfPresent(String.valueOf(key), (ignored, sockets) -> {
            sockets.remove(session.getId());
            return sockets.isEmpty() ? null : sockets;
        });
    }

    /**
     * Разрыв соединения — обычное дело: игрок закрыл вкладку, и кадр улетает
     * в мёртвый сокет. Это не поломка и в лог как поломка не идёт.
     */
    protected void send(WebSocketSession session, Object frame) {
        synchronized (session) {
            if (!session.isOpen()) {
                return;
            }
            try {
                session.sendMessage(new TextMessage(mapper.writeValueAsString(frame)));
            } catch (IOException e) {
                log.debug("Клиент отключился, кадр не доставлен: {}", e.getMessage());
            }
        }
    }
}
