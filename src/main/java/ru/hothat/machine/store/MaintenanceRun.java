package ru.hothat.machine.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * История прогонов уборки.
 *
 * <p>Сегодня истории нет вовсе: у прохода по комнатам есть кулдаун и есть
 * ответ вызывающему, но узнать задним числом, когда уборка шла и что сделала,
 * негде. Первый же вопрос «куда делась комната» упирается в это.
 *
 * <p>Счётчика два, а не один, и это не избыточность: сохранённые кем-то записи
 * просматриваются, но не удаляются, поэтому «просмотрено» почти всегда больше
 * «тронуто». Одно число не различило бы пустой прогон и прогон, где всё
 * оказалось живым.
 *
 * <p>{@code skipped_cooldown} — тоже исход прогона, а не отсутствие прогона:
 * сегодня отказ приезжает ключом {@code skipped:true} внутри вложенного
 * объекта, и внешний планировщик о нём не узнаёт вовсе.
 */
@Entity
@Table(name = "maintenance_run", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MaintenanceRun {

    /** Кто позвал; набор закрыт ограничением базы. */
    static final String CRON = "cron";
    static final String ADMIN = "admin";
    static final String AGENT = "agent";

    /** Чем кончилось; набор закрыт ограничением базы. */
    static final String RUNNING = "running";
    static final String SUCCEEDED = "succeeded";
    static final String FAILED = "failed";
    static final String SKIPPED_COOLDOWN = "skipped_cooldown";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 40)
    private String job;

    @Builder.Default
    @Column(name = "triggered_by", nullable = false, updatable = false, length = 16)
    private String triggeredBy = CRON;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String state = RUNNING;

    @Builder.Default
    @Column(name = "checked_count", nullable = false)
    private Integer checkedCount = 0;

    @Builder.Default
    @Column(name = "affected_count", nullable = false)
    private Integer affectedCount = 0;

    @Column(name = "failure_reason", length = 400)
    private String failureReason;

    @Builder.Default
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;
}
