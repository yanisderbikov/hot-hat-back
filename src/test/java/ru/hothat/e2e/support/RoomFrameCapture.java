package ru.hothat.e2e.support;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Приветственный кадр свежего сокета {@code /ws/v2/room/{roomId}} — снимок
 * комнаты глазами зрителя в этот момент.
 *
 * <p>Метод держится на том, что приветствие и обновление канал строит одним
 * и тем же вызовом сборщика ({@code RoomSocketHandler.hello()} и
 * {@code event()} оба зовут {@code streamRoom.run}); различается только
 * {@code type}. Поэтому не надо держать сокет открытым и ждать «кадр с
 * признаком»: после HTTP-действия открывается новый сокет, и первый кадр —
 * это и есть состояние после действия.
 *
 * <p>Кадр возвращается как есть, без утверждения {@code type == "hello"}:
 * постороннему — выгнанному или вышедшему — сервер отвечает кадром
 * {@code error} и закрывает сокет, и слепок должен зафиксировать именно это.
 */
public final class RoomFrameCapture {

    private RoomFrameCapture() {
    }

    public static JsonNode hello(E2ETest ctx, Player viewer, String roomId) throws Exception {
        Channel channel = Channel.open(ctx.ws("/ws/v2/room/" + roomId, viewer));
        try {
            return channel.nextFrame("приветственный кадр комнаты для " + viewer.nickname()).body();
        } finally {
            closeQuietly(channel);
        }
    }

    /** Сокет постороннего сервер закрыл сам; закрывать его второй раз — не ошибка теста. */
    private static void closeQuietly(Channel channel) {
        try {
            channel.close();
        } catch (Exception ignored) {
            // Соединение уже закрыто сервером — закрывать нечего.
        }
    }
}
