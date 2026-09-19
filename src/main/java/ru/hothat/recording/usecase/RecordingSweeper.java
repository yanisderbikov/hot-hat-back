package ru.hothat.recording.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.hothat.recording.port.RecordingStoragePort;
import ru.hothat.recording.spi.RecordingLifecyclePort;
import ru.hothat.recording.store.RecordingStore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Уборка записей, которым вышел срок.
 *
 * <p>Сохранённые кем-то записи в прогон не попадают ВООБЩЕ: их отсекает
 * частичный индекс {@code ix_recording_retention_expired}, а не проверка после
 * чтения. Раньше читались все просроченные строки подряд и каждая
 * проверялась в памяти двумя разными способами — по счётчику и по длине
 * массива, — которые между собой расходились.
 *
 * <p>Обращений к базе в цикле нет: список берётся одним запросом, карточки —
 * пачкой, снятие с учёта — одной транзакцией на весь прогон. В цикле остаётся
 * только удаление объектов из бакета, и оно там неизбежно: у хранилища нет
 * пакетного удаления, а держать транзакцию поверх этих вызовов — ровно та
 * ошибка, от которой уезжали.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordingSweeper {

    private final RecordingWrites writes;
    private final RecordingStore store;
    private final RecordingStoragePort storage;

    public RecordingLifecyclePort.SweepResult sweep(int limit) {
        List<UUID> expired = writes.expired(limit);
        if (expired.isEmpty()) {
            return new RecordingLifecyclePort.SweepResult(0, 0, List.of());
        }
        List<RecordingStore.Card> cards = store.cards(expired);
        List<UUID> retired = new ArrayList<>(cards.size());
        List<String> names = new ArrayList<>(cards.size());
        for (RecordingStore.Card card : cards) {
            RecordingStore.Artifact artifact = card.artifact();
            if (artifact != null && artifact.deletedAtMs() == null) {
                storage.delete(artifact.objectPath());
            }
            retired.add(card.passport().id());
            names.add(card.passport().recordingId());
        }
        writes.retireAll(retired);
        log.info("Уборка записей: просмотрено {}, убрано {}", cards.size(), retired.size());
        return new RecordingLifecyclePort.SweepResult(cards.size(), retired.size(), names);
    }
}
