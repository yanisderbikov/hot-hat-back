package ru.hothat.recording.store;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Дверь в {@code v2.recording_retention} — срок хранения и счётчик сохранений.
 *
 * <p>Счётчик меняется ОДНИМ условным обновлением, без чтения в память (§6.4).
 * Раньше счётчиков было два — {@code saved_count} и длина массива
 * {@code saved_by}, — и при одновременном сохранении двумя игроками они
 * расходились: оба читали одно и то же число и писали одно и то же плюс один.
 *
 * <p>Срок и счётчик двигаются в одном операторе, потому что база требует их
 * согласованности: сохранённая запись не имеет срока, несохранённая обязана
 * его иметь. Два отдельных обновления между собой оставляли бы строку в
 * состоянии, которого ограничение не допускает.
 */
@Repository
interface RecordingRetentions extends JpaRepository<RecordingRetention, UUID> {

    List<RecordingRetention> findByRecordingIdIn(Collection<UUID> recordingIds);

    /**
     * Просроченные и никем не сохранённые. Частичный индекс
     * {@code ix_recording_retention_expired} отвечает целиком: сохранённые в
     * него не попадают вовсе, а их со временем становится большинство.
     */
    List<RecordingRetention> findBySaveCountAndExpiresAtLessThanOrderByExpiresAt(
            Integer saveCount, Instant before, Limit limit);

    /** Игрок положил запись к себе: сроку хранения конец. */
    @Modifying
    @Query("""
            update RecordingRetention r
               set r.saveCount = r.saveCount + 1, r.expiresAt = null
             where r.recordingId = :recordingId""")
    int countSave(@Param("recordingId") UUID recordingId);

    /**
     * Игрок забрал запись из библиотеки. Последний ушедший возвращает записи
     * обычный срок: иначе она осталась бы в бакете навсегда.
     *
     * <p>Ветка «остались другие хранители» переписывает срок сам в себя, а не
     * в {@code null}, и это не хитрость: при {@code save_count > 1} он уже
     * пуст — за этим следит ограничение {@code ck_recording_retention_expiry}.
     * Литеральный {@code null} рядом с параметром в одном {@code case} база
     * принять не может: тип выражения ей взять неоткуда, и она читает
     * параметр как текст.
     */
    @Modifying
    @Query("""
            update RecordingRetention r
               set r.saveCount = case when r.saveCount > 0 then r.saveCount - 1 else 0 end,
                   r.expiresAt = case when r.saveCount > 1 then r.expiresAt else :expiry end
             where r.recordingId = :recordingId""")
    int countForget(@Param("recordingId") UUID recordingId, @Param("expiry") Instant expiry);
}
