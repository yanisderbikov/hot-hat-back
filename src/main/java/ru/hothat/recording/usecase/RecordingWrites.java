package ru.hothat.recording.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.recording.domain.EgressStage;
import ru.hothat.recording.domain.RecordingKey;
import ru.hothat.recording.port.EgressControlPort;
import ru.hothat.recording.port.RoomSnapshotPort;
import ru.hothat.recording.store.RecordingStore;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Короткие транзакции области записей.
 *
 * <p>Каждый метод — одна транзакция, и ни в одной нет сетевого вызова. Это
 * ответ на находку B5: раньше транзакция открывалась до обращения к LiveKit и
 * держала соединение из пула все двадцать секунд его таймаута — при пуле в
 * десять соединений десяти одновременных стартов хватало, чтобы сервер
 * перестал отвечать вообще на всё.
 *
 * <p>Порядок теперь такой: короткая транзакция «застолбить», вызов LiveKit
 * СНАРУЖИ, короткая транзакция «применить». Сценарий видит этот порядок
 * прямым текстом, а не прячет его внутри одного большого метода.
 *
 * <p>Отдельным классом — потому что вызов транзакционного метода изнутри
 * своего же класса проходит мимо прокси Spring и транзакции не открывает.
 */
@Component
@RequiredArgsConstructor
public class RecordingWrites {

    /**
     * Столько живёт захват старта. Раньше это выражалось колонкой
     * {@code start_lock_at_ms} — «время, до которого чужой старт считается
     * недавним»; теперь достаточно отметки запроса самого задания.
     */
    private static final long START_RETRY_MS = 30_000;

    private final RecordingStore store;
    private final Clock clock;

    /**
     * Застолбить старт.
     *
     * <p>Возвращает {@code false}, если снимать уже начали: две вкладки хозяина
     * не заведут два задания на одну партию. Раньше это держалось склейкой
     * строк в идентификаторе и секундной блокировкой; теперь ключ
     * идемпотентности — уникальный индекс {@code ux_recording_game}.
     */
    @Transactional
    public Reservation reserveStart(RecordingKey key, String startedByUid,
                                    RoomSnapshotPort.RoomSnapshot room, boolean prewarm) {
        Instant now = clock.instant();
        Optional<RecordingStore.Card> existing = store.card(key);
        if (existing.isPresent()) {
            RecordingStore.Card card = existing.get();
            RecordingStore.Job job = card.job();
            boolean running = job != null && (
                    (job.egressId() != null && !job.egressId().isBlank())
                            || !EgressStage.FAILED.wire().equals(job.state()));
            // Провалившийся старт можно повторить, но не сразу: иначе два
            // клиента, увидевшие ошибку одновременно, попросят у LiveKit два
            // задания на одну партию.
            boolean tooSoon = job != null && now.toEpochMilli() - job.startRequestedAtMs() < START_RETRY_MS;
            if (running || tooSoon) {
                return new Reservation(card.passport().id(), false, card);
            }
        }
        UUID id = store.openPassport(key, startedByUid, room, prewarm, now);
        return new Reservation(id, true, null);
    }

    /** LiveKit принял запрос — записываем это в строку ЗАДАНИЯ и только в неё. */
    @Transactional
    public void applyStarted(UUID id, EgressControlPort.Snapshot started) {
        store.attachEgress(id, started, clock.instant());
    }

    /** LiveKit отказал: у провала обязана быть названа причина. */
    @Transactional
    public void applyStartFailure(UUID id, String reason) {
        store.failJob(id, reason, clock.instant());
    }

    @Transactional
    public void markStopRequested(UUID id) {
        store.markStopRequested(id, clock.instant());
    }

    /**
     * Применить ответ LiveKit: стадию — заданию, файл — артефакту.
     *
     * <p>Две таблицы, но два РАЗНЫХ факта из одного ответа, и каждый уезжает
     * своему писателю. Сводить их в одну строку было ошибкой прошлой схемы.
     *
     * @return стадия задания после применения
     */
    @Transactional
    public EgressStage applySnapshot(UUID id, RecordingKey key, EgressControlPort.Snapshot snapshot, String bucket) {
        Instant now = clock.instant();
        EgressStage stage = store.applyEgressSnapshot(id, snapshot, now);
        if (stage == EgressStage.COMPLETE) {
            // Путь считается из пары «комната + партия» и потому известен
            // заранее; имя из ответа LiveKit берётся, только если оно есть.
            String path = snapshot.filename() == null || snapshot.filename().isBlank()
                    ? key.objectPath() : snapshot.filename();
            store.putArtifact(id, path, bucket, snapshot.sizeBytes(), snapshot.durationNs(), now);
        }
        return stage;
    }

    /** Файл нашёлся в бакете сам — вебхука могло и не быть вовсе. */
    @Transactional
    public void confirmArtifact(UUID id, RecordingKey key, long sizeBytes, String bucket) {
        store.putArtifact(id, key.objectPath(), bucket, sizeBytes, 0L, clock.instant());
    }

    /** Заморозить итог партии: счёт, победителя и число слов. */
    @Transactional
    public void freezeResults(UUID id, RoomSnapshotPort.RoomSnapshot room) {
        store.freezeResults(id, room);
    }

    @Transactional
    public void markRecorderReady(UUID id, String phase, String identity) {
        store.markRecorderReady(id, phase, identity, clock.instant());
    }

    @Transactional
    public void markRecorderStarted(UUID id, String phase, String identity) {
        store.markRecorderStarted(id, phase, identity, clock.instant());
    }

    @Transactional
    public void markCeremonyCompleted(UUID id) {
        store.markCeremonyCompleted(id, clock.instant());
    }

    /**
     * Записать доставку вебхука.
     *
     * @return {@code true} — доставка новая; {@code false} — повтор, который
     *         раньше применялся второй раз, потому что отличить его было не по чему
     */
    @Transactional
    public boolean journal(String deliveryId, UUID recordingId, String eventName,
                           String egressId, String egressState, boolean applied,
                           Map<String, Object> payload) {
        return store.journal(deliveryId, recordingId, eventName, egressId, egressState,
                applied, payload, clock.instant());
    }

    @Transactional
    public boolean save(UUID id, String uid) {
        return store.save(id, uid);
    }

    @Transactional
    public boolean forget(UUID id, String uid) {
        return store.forget(id, uid, clock.instant());
    }

    /** Записи, которым вышел срок и которых никто не сохранил. */
    @Transactional(readOnly = true)
    public List<UUID> expired(int limit) {
        return store.expired(clock.instant(), limit);
    }

    /**
     * Снять с учёта весь прогон уборки одной транзакцией.
     *
     * <p>Именно одной: сто записей — это одна транзакция, а не сто. Обе
     * таблицы говорят об одном факте «файла больше нет»: артефакт помнит,
     * когда он исчез, а строка срока уходит совсем — иначе прогон возвращался
     * бы к этой записи каждый раз. Файлы к этому моменту уже убраны, и делать
     * это снаружи транзакции обязательно.
     */
    @Transactional
    public void retireAll(List<UUID> ids) {
        Instant now = clock.instant();
        for (UUID id : ids) {
            store.markArtifactDeleted(id, now);
            store.dropRetention(id);
        }
    }

    /** Итог попытки застолбить старт. */
    public record Reservation(UUID recordingId, boolean fresh, RecordingStore.Card running) {
    }
}
