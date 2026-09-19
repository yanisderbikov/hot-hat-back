package ru.hothat.app.spi;

import java.time.Instant;
import java.util.List;

/**
 * Что область {@code app} отвечает соседям о продуктовых событиях.
 *
 * <p>Спрашивает её консоль администратора: статистика периода — это счёт по
 * журналу событий. Раньше консоль читала таблицу событий сама, через общий
 * {@code GetterOps}, и разбирала jsonb каждой строки; теперь она получает
 * готовые записи, а разбор остался у владельца таблицы.
 *
 * <p>Порт только читает: событие кладёт браузер, и второй писатель у журнала
 * означал бы событие, которого не было.
 */
public interface ProductEventPort {

    /**
     * События периода, не больше предела просмотра.
     *
     * <p>Предел здесь не про здравый смысл, а про память: события приезжают
     * в кучу целиком. Вызывающий сравнивает размер ответа с пределом и
     * показывает администратору, что числа занижены.
     */
    List<ProductEventRow> between(Instant from, Instant to, int limit);

    /** Событие в объёме, который нужен отчёту. */
    record ProductEventRow(String eventType,
                           String uid,
                           String roomId,
                           int playerCount,
                           int teamCount,
                           int wordCount,
                           Instant occurredAt) {
    }
}
