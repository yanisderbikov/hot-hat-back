package ru.hothat.e2e.support;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Сокет с очередью входящих: тест ждёт кадр, а не опрашивает состояние.
 *
 * <p>Первым кадром любой канал присылает приветствие — сервер шлёт его прямо
 * из {@code afterConnectionEstablished}, ещё до того, как клиенту вернётся
 * сессия. Поэтому очередь заводится до рукопожатия, а тест обязан снять
 * приветствие сам, иначе «кадр после действия» окажется приветственным.
 *
 * <p>{@link #next()} отвечает {@code null}, а не бросает: тест сам решает,
 * что значит тишина, и подписывает падение своими словами.
 */
public final class Channel implements AutoCloseable {

    /** Одно ожидание — и рукопожатия, и каждого кадра. */
    public static final long FRAME_TIMEOUT_SECONDS = 5;

    /**
     * Потолок текстового кадра у клиента Tomcat по умолчанию — 8 КиБ. Кадр
     * комнаты с апелляцией по двадцати словам и клипом Подмены его превышает,
     * и клиент молча закрывал бы сокет кодом 1009, а тест видел бы «тишину».
     * Браузер такого потолка не знает, поэтому это ограничение только теста.
     */
    private static final int MAX_FRAME_BYTES = 4 * 1024 * 1024;

    private final WebSocketSession session;
    private final BlockingQueue<String> inbox;

    private Channel(WebSocketSession session, BlockingQueue<String> inbox) {
        this.session = session;
        this.inbox = inbox;
    }

    public static Channel open(URI uri) throws Exception {
        // Очередь создаётся до рукопожатия: приветственный кадр может
        // прийти раньше, чем execute() вернёт сессию.
        BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxTextMessageBufferSize(MAX_FRAME_BYTES);
        WebSocketSession session = new StandardWebSocketClient(container).execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                inbox.add(message.getPayload());
            }
        }, null, uri).get(FRAME_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return new Channel(session, inbox);
    }

    public void send(String text) throws Exception {
        session.sendMessage(new TextMessage(text));
    }

    /** Следующий кадр либо null, если за отведённое время ничего не пришло. */
    public String next() throws InterruptedException {
        return inbox.poll(FRAME_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Следующий кадр разобранным; тишина — это падение с названной причиной.
     *
     * @param expectation что за кадр ждали — уходит в сообщение об ошибке
     */
    public Frame nextFrame(String expectation) throws InterruptedException {
        String raw = next();
        if (raw == null) {
            throw new AssertionError("За " + FRAME_TIMEOUT_SECONDS + " с не пришёл кадр: " + expectation);
        }
        return Frame.parse(raw);
    }

    /**
     * Ждать кадр, на котором сбудется условие.
     *
     * <p>Кадр — снимок целиком, поэтому проверяется состояние, а не «что
     * изменилось»; а читается до совпадения, а не ровно один: между открытием
     * канала и проверяемым действием сервер вправе прислать и другой кадр —
     * вход игрока, например, даёт {@code roomChanged} на месте и
     * {@code roomRowChanged} на счётчике живых, то есть два кадра подряд.
     *
     * @param expectation что за кадр ждали — уходит в сообщение об ошибке
     */
    public Frame awaitFrame(String expectation, Predicate<Frame> condition) throws InterruptedException {
        List<String> seen = new ArrayList<>();
        for (int attempt = 0; attempt < 6; attempt++) {
            String raw = next();
            if (raw == null) {
                break;
            }
            Frame frame = Frame.parse(raw);
            if (condition.test(frame)) {
                return frame;
            }
            seen.add(frame.raw());
        }
        throw new AssertionError("Не дождались: " + expectation + "; пришло кадров " + seen.size()
                + (seen.isEmpty() ? "" : ", последний: " + seen.get(seen.size() - 1)));
    }

    @Override
    public void close() throws Exception {
        session.close();
    }
}
