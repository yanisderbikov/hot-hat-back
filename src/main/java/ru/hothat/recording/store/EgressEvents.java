package ru.hothat.recording.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Дверь в {@code v2.recording_egress_event} — журнал уведомлений LiveKit.
 *
 * <p>Единственный вопрос к нему в горячем пути: «эту доставку уже применяли?».
 * Раньше отличить повтор было не по чему, и он применялся второй раз.
 */
@Repository
interface EgressEvents extends JpaRepository<EgressEvent, Long> {

    boolean existsByDeliveryId(String deliveryId);
}
