package ru.hothat.recording.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Жизненный цикл задания Egress.
 *
 * <p>Собирает всё, что в {@code game_recording} относилось к LiveKit:
 * {@code egress_id}, {@code livekit_status}, {@code start_lock_at_ms},
 * {@code last_stop_attempt_at_ms}, {@code last_egress_sync_at_ms},
 * {@code egress_active_at_ms}, {@code egress_ended_at_ms}, {@code start_error},
 * {@code egress_error}. Девять колонок, которые писали вебхуки и опрос, лежали
 * в одной строке с паспортом партии и со сроком хранения — то есть вебхук
 * LiveKit переписывал заодно и счётчик сохранений.
 *
 * <p>{@link #state} — единственный статус записи наружу (§6.3). Сырой код
 * LiveKit сохранён только в журнале вебхуков: игроку он не нужен, а наш набор
 * стадий от него не зависит.
 *
 * <p>Колонки-замка нет. Сегодня захват старта выражен {@code start_lock_at_ms}
 * — «время, до которого чужой старт считается недавним». Здесь строка берётся
 * {@code SELECT … FOR UPDATE}, внешний вызов LiveKit идёт ВНЕ транзакции, а
 * результат применяется второй короткой транзакцией по {@code @Version}. Это
 * прямой ответ на находку B5, где транзакция держалась поверх сетевого вызова
 * с таймаутом двадцать секунд при пуле в десять соединений.
 */
@Entity
@Table(name = "recording_egress_job", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class EgressJob {

    /** Стадия задания; набор закрыт ограничением базы. */
    static final String STARTING = "starting";
    static final String ACTIVE = "active";
    static final String PROCESSING = "processing";
    static final String COMPLETE = "complete";
    static final String FAILED = "failed";

    @Id
    @Column(name = "recording_id", nullable = false, updatable = false)
    private UUID recordingId;

    /** Пусто, пока LiveKit не ответил на запрос старта. */
    @Column(name = "egress_id", length = 120)
    private String egressId;

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String state = STARTING;

    @Builder.Default
    @Column(name = "start_requested_at", nullable = false, updatable = false)
    private Instant startRequestedAt = Instant.now();

    @Column(name = "active_at")
    private Instant activeAt;

    @Column(name = "stop_requested_at")
    private Instant stopRequestedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    /** Когда последний раз сверялись с LiveKit опросом ListEgress. */
    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    /**
     * Причина провала. Одна колонка вместо пары start_error и egress_error:
     * какая из них заполнена, зависело от того, на каком шаге сорвалось, и
     * читателю приходилось смотреть в обе.
     */
    @Column(name = "failure_reason", length = 600)
    private String failureReason;

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
