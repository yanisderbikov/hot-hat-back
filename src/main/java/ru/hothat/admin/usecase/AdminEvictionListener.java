package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.hothat.admin.port.VideoRoomPort;

/**
 * Выставляет из видеокомнаты после коммита.
 *
 * <p>{@code AFTER_COMMIT} — не украшение: если транзакция откатится, человек
 * не забанен, и выкидывать его из видеосвязи не за что. Обратный порядок
 * (сначала видеослужба, потом база) давал бы вылет без бана.
 *
 * <p>Отказ видеослужбы гасит порт и не выходит сюда: бан в базе состоялся, и
 * отменять его из-за недоступного LiveKit нельзя. Участник без токена всё
 * равно не вернётся — проверка отвергнет его на входе.
 */
@Component
@RequiredArgsConstructor
public class AdminEvictionListener {

    private final VideoRoomPort videoRooms;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlayerBanned(AdminEvictionEvents.PlayerBanned event) {
        videoRooms.evict(event.roomId(), event.uid());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomClosed(AdminEvictionEvents.RoomClosedByAdmin event) {
        videoRooms.close(event.roomId());
    }
}
