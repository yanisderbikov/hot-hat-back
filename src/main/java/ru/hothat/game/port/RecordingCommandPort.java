package ru.hothat.game.port;

/**
 * Запись партии глазами партии.
 *
 * <p>Партия умеет ровно одно: сказать, что писать больше нечего. Начинает
 * запись область записей сама, по своим правилам готовности рекордера.
 */
public interface RecordingCommandPort {

    /** Остановить запись: партия кончилась технически или комната закрылась. */
    void finish(String roomId, int gameNumber, String reason);
}
