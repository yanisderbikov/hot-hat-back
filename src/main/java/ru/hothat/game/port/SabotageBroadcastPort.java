package ru.hothat.game.port;

import ru.hothat.game.domain.SabotageEvent;

/**
 * Рассылка диверсии всем зрителям сцены.
 *
 * <p>Это ускорение, а не источник правды: событие уже записано в партию, и
 * клиент увидит его следующим снимком в любом случае. Поэтому рассылка идёт
 * после фиксации транзакции и её отказ ничего не отменяет.
 */
public interface SabotageBroadcastPort {

    void publish(String roomId, SabotageEvent event);
}
