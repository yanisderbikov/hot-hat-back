package ru.hothat.recording.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.hothat.recording.domain.EgressStage;
import ru.hothat.recording.domain.RecordingKey;
import ru.hothat.recording.port.EgressControlPort;
import ru.hothat.recording.port.RecordingStoragePort;
import ru.hothat.recording.store.RecordingStore;

import java.time.Clock;
import java.util.Optional;

/**
 * Подтянуть состояние зависшей записи из LiveKit и хранилища.
 *
 * <p>Нужно потому, что вебхук может не прийти: LiveKit присылает его один раз
 * и не повторяет. Опроса по расписанию при этом нет — сверка идёт по случаю,
 * когда карточку и так спросили, и не чаще раза в пятнадцать секунд.
 *
 * <p>Оба обращения — сетевые и оба ВНЕ транзакции. Раньше вся эта работа шла
 * внутри одной транзакции, да ещё и по сотне записей подряд в списке
 * библиотеки: сто карточек означали до двухсот сетевых вызовов под одним
 * соединением из пула. Из списка сверка убрана совсем — там она и не нужна:
 * сохранённая запись давно завершена.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EgressRefresher {

    /** Реже, чем раз в пятнадцать секунд, спрашивать LiveKit бессмысленно. */
    private static final long SYNC_INTERVAL_MS = 15_000;

    private final EgressControlPort egress;
    private final RecordingStoragePort storage;
    private final RecordingWrites writes;
    private final RecordingStore store;
    private final Clock clock;

    /**
     * Сверить и вернуть карточку заново.
     *
     * <p>Если сверять нечего — та же карточка возвращается без единого
     * обращения наружу.
     */
    public RecordingStore.Card refresh(RecordingKey key, RecordingStore.Card card) {
        RecordingStore.Job job = card.job();
        if (job == null || !EgressStage.ofWire(job.state()).running()) {
            return card;
        }
        boolean changed = false;
        long now = clock.millis();
        boolean stale = job.lastSyncAtMs() == null || now - job.lastSyncAtMs() > SYNC_INTERVAL_MS;
        if (stale && job.egressId() != null && !job.egressId().isBlank()) {
            Optional<EgressControlPort.Snapshot> answer = egress.describe(job.egressId());
            if (answer.isPresent()) {
                writes.applySnapshot(card.passport().id(), key, answer.get(), storage.bucket());
                changed = true;
            }
        }
        // Файл в бакете — доказательство сильнее любого статуса: если он там
        // лежит, съёмка кончилась, чем бы ни отвечал LiveKit.
        if (card.artifact() == null) {
            Optional<Long> size = storage.contentLength(key.objectPath());
            if (size.isPresent()) {
                writes.confirmArtifact(card.passport().id(), key, size.get(), storage.bucket());
                changed = true;
            }
        }
        return changed ? store.card(card.passport().id()).orElse(card) : card;
    }
}
