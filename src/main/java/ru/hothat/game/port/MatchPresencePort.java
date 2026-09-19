package ru.hothat.game.port;

import java.util.List;

/**
 * Кто сейчас на видеосвязи.
 *
 * <p>Единственный внешний источник правды о присутствии: строка игрока говорит
 * лишь о том, когда он в последний раз о себе напомнил, а «здесь ли он сейчас»
 * знает только сервер видеосвязи.
 *
 * <p>Вызов сетевой и медленный, поэтому делается до открытия транзакции —
 * держать транзакцию на время похода в LiveKit нельзя.
 */
public interface MatchPresencePort {

    /**
     * Список участников комнаты по данным видеосвязи.
     *
     * @return пусто, если связь недоступна: это не то же самое, что «никого нет»
     */
    Roster roster(String roomId);

    /** Убрать участника со связи: так уходят превью-подключения главной страницы. */
    void disconnect(String roomId, String identity);

    /**
     * @param available удалось ли спросить сервер видеосвязи; при {@code false}
     *                  список пуст и решать по нему ничего нельзя
     */
    record Roster(boolean available, List<String> identities) {

        public static Roster unavailable() {
            return new Roster(false, List.of());
        }
    }
}
