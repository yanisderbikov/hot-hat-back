package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.hothat.game.port.RecordingCommandPort;
import ru.hothat.game.port.SabotageBroadcastPort;

/**
 * Что делается после фиксации транзакции партии.
 *
 * <p>Рассылка диверсии — ускорение: событие уже записано, и сцена увидела бы
 * его следующим снимком. Остановка записи — уборка за кончившейся партией.
 * Ни то ни другое не является инвариантом, поэтому им и позволено опоздать.
 */
@Component
@RequiredArgsConstructor
public class MatchEventListeners {

    private final SabotageBroadcastPort broadcast;
    private final RecordingCommandPort recordings;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSabotageApplied(MatchEvents.SabotageApplied applied) {
        broadcast.publish(applied.roomId(), applied.event());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMatchTerminated(MatchEvents.MatchTerminated terminated) {
        recordings.finish(terminated.roomId(), terminated.gameNumber(), terminated.reason());
    }
}
