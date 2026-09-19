package ru.hothat.realtime.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.WebSocketSession;
import ru.hothat.config.ApiException;
import ru.hothat.config.ErrorMessages;
import ru.hothat.config.GlobalExceptionHandler;
import ru.hothat.realtime.api.dto.ChannelErrorFrameDTO;
import ru.hothat.realtime.api.dto.LobbyChannelEventDTO;
import ru.hothat.realtime.api.dto.LobbyChannelHelloDTO;
import ru.hothat.realtime.api.dto.RoomPreviewEventDTO;
import ru.hothat.realtime.spi.RealtimeEvents;
import ru.hothat.realtime.usecase.SpotlightLobbyRoomUseCase;
import ru.hothat.realtime.usecase.StreamLobbyChannelUseCase;
import ru.hothat.util.Ids;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Канал витрины — {@code /ws/v2/lobby}.
 *
 * <p>Заменяет живую подписку главной на все открытые комнаты
 * ({@code home/home.js:95}) и, кадром {@code spotlight}, ещё четыре подписки
 * выбранной комнаты ({@code home/home.js:90}, {@code live-preview.js:34}).
 * Пять подписок на посетителя главной сходятся в один сокет, и приватная
 * комната больше не покидает сервер, чтобы быть отфильтрованной в браузере.
 *
 * <p>Единственный канал, открытый гостю: главная — это то, ради чего он и
 * пришёл. Рукопожатие для него заведено отдельным экземпляром
 * {@link ChannelHandshakeAuthenticator}; остальные каналы гостя не пускают.
 *
 * <p><b>Почему витрина рассылается по расписанию, а не сразу.</b> Строка
 * комнаты пишется на каждом отгаданном слове, а витрину это не меняет: в ней
 * фаза, название и число живых. Немедленная рассылка означала бы, что во время
 * хода в любой комнате список перестраивается несколько раз в секунду —
 * и не одному зрителю, а каждому, кто открыл главную, причём каждому своим
 * запросом. Поэтому событие только помечает витрину устаревшей, а склеенная
 * рассылка уходит не чаще раза в две секунды. Для списка комнат это незаметно,
 * а для сервера — разница между шестью запросами и шестьюстами.
 *
 * <p>Превью, наоборот, уходит сразу: это идущая партия, и опоздание видно.
 */
@Component
@Slf4j
public class LobbySocketHandler extends ChannelSocketHandler {

    /** Витрина одна на всех: ключ рассылки постоянный. */
    private static final String LOBBY = "lobby";

    /** Имя сменной подписки — им же её снимают кадром unsubscribe. */
    private static final String SPOTLIGHT = "spotlight";

    /** Комната, выбранная этим сокетом для превью. */
    private static final String SPOTLIGHT_ATTRIBUTE = "lobbySpotlight";

    /** Как часто склеенная витрина уходит подписчикам, миллисекунды. */
    private static final long FLUSH_DELAY_MS = 2000;

    private final StreamLobbyChannelUseCase streamLobby;
    private final SpotlightLobbyRoomUseCase spotlightRoom;

    /** Витрина изменилась и ждёт ближайшей рассылки. */
    private final AtomicBoolean stale = new AtomicBoolean(false);

    public LobbySocketHandler(ObjectMapper mapper,
                              StreamLobbyChannelUseCase streamLobby,
                              SpotlightLobbyRoomUseCase spotlightRoom) {
        super(mapper);
        this.streamLobby = streamLobby;
        this.spotlightRoom = spotlightRoom;
    }

    @Override
    protected String key(WebSocketSession session) {
        return LOBBY;
    }

    @Override
    protected Object hello(Subscriber subscriber) {
        return new LobbyChannelHelloDTO(underPrincipal(subscriber, () -> streamLobby.run(subscriber.user())));
    }

    @Override
    protected Object event(Subscriber subscriber) {
        return new LobbyChannelEventDTO(underPrincipal(subscriber, () -> streamLobby.run(subscriber.user())));
    }

    /**
     * Кадр {@code spotlight}: показать другую комнату крупным планом.
     *
     * <p>Идентификатор проверяется тем же выражением, что и {@code @RoomId}
     * на адресах HTTP: транспорт не должен пускать дальше то, что разбор пути
     * потом отвергнет. Право на превью проверяет сценарий — и проверяет
     * заново на каждом кадре.
     */
    @Override
    protected boolean onFrame(WebSocketSession session, String type, JsonNode frame) {
        if (!SPOTLIGHT.equals(type)) {
            return false;
        }
        String roomId = frame.path("roomId").asText("").trim().toLowerCase();
        if (!Ids.ROOM.matcher(roomId).matches()) {
            send(session, new ChannelErrorFrameDTO(
                    ErrorMessages.resolve("ROOM_INVALID", 400), "ROOM_INVALID"));
            return true;
        }
        // Новая комната заменяет прежнюю: второго превью в одном сокете не бывает.
        session.getAttributes().put(SPOTLIGHT_ATTRIBUTE, roomId);
        Subscriber subscriber = subscriber(session);
        if (subscriber != null) {
            // null означает, что сокет уже снял подписку кадром unsubscribe:
            // просить превью после отказа — не ошибка, но и показывать нечего.
            sendPreview(subscriber, roomId);
        }
        return true;
    }

    /** {@code unsubscribe} с {@code id=spotlight} снимает превью, не трогая витрину. */
    @Override
    protected void unsubscribe(WebSocketSession session, String id) {
        if (SPOTLIGHT.equals(id)) {
            session.getAttributes().remove(SPOTLIGHT_ATTRIBUTE);
            return;
        }
        super.unsubscribe(session, id);
    }

    /**
     * Витрина устарела: помечаем и ждём ближайшей склеенной рассылки.
     *
     * <p>{@code AFTER_COMMIT} — до фиксации зрители увидели бы комнату,
     * которой при откате не станет. Читать здесь нечего: чтение делает
     * рассылка, и делает его на каждого зрителя отдельно — витрина у каждого
     * своя, чужую тестовую комнату видит только её владелец.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLobbyChanged(RealtimeEvents.LobbyChanged event) {
        stale.set(true);
    }

    /**
     * Превью выбранной комнаты — сразу и только тем, кто смотрит именно её.
     *
     * <p>{@code REQUIRES_NEW} нужен вместе с {@code AFTER_COMMIT}: транзакция
     * писателя уже зафиксирована, но ещё связана с потоком, и вложенное чтение
     * присоединилось бы к завершённой.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onRoomChanged(RealtimeEvents.RoomChanged event) {
        for (Subscriber subscriber : subscribers(LOBBY)) {
            Object watched = subscriber.session().getAttributes().get(SPOTLIGHT_ATTRIBUTE);
            if (event.roomId().equals(watched)) {
                sendPreview(subscriber, event.roomId());
            }
        }
    }

    /**
     * Склеенная рассылка витрины.
     *
     * <p>Своя транзакция открывается внутри сценария чтения, поэтому здесь её
     * нет: задача планировщика — не часть чьего-либо запроса.
     */
    @Scheduled(fixedDelay = FLUSH_DELAY_MS)
    public void flush() {
        if (stale.compareAndSet(true, false)) {
            broadcast(LOBBY);
        }
    }

    private void sendPreview(Subscriber subscriber, String roomId) {
        if (!subscriber.session().isOpen()) {
            return;
        }
        try {
            send(subscriber.session(), new RoomPreviewEventDTO(
                    underPrincipal(subscriber, () -> spotlightRoom.run(subscriber.user(), roomId))));
        } catch (ApiException e) {
            // Комнату закрыли или сделали приватной, пока превью было открыто:
            // показ прекращается, но канал витрины остаётся живым — главной
            // есть что показывать и без этой комнаты.
            subscriber.session().getAttributes().remove(SPOTLIGHT_ATTRIBUTE);
            send(subscriber.session(), new ChannelErrorFrameDTO(
                    ErrorMessages.resolve(e.getCode(), e.getStatus()),
                    ErrorMessages.publicCode(e.getCode())));
        } catch (AccessDeniedException e) {
            // Тот же исход, но отказала роль: гостя разжаловали или забанили,
            // пока превью было открыто. Витрину это не закрывает — её сценарий
            // откажет сам на ближайшей склеенной рассылке.
            subscriber.session().getAttributes().remove(SPOTLIGHT_ATTRIBUTE);
            send(subscriber.session(), new ChannelErrorFrameDTO(
                    ErrorMessages.resolve(GlobalExceptionHandler.deniedCode(subscriber.user()), 403),
                    GlobalExceptionHandler.deniedCode(subscriber.user())));
        } catch (RuntimeException e) {
            log.debug("Превью комнаты не отправлено: {}", e.toString());
        }
    }
}
