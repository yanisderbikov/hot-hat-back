package ru.hothat.admin.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Одна метрика снимка расхода.
 *
 * <p>Двенадцать строк вместо двенадцати вложенных объектов jsonb:
 * {@code database.reads/writes/deletes/storage},
 * {@code hosting.transfer/storage}, {@code auth},
 * {@code vps.disk/memory/mediaStorage/networkTotal/networkMonthly},
 * {@code mail.daily/monthly}.
 *
 * <p>Выводимого здесь нет: {@code remaining}, {@code percent} и
 * {@code available} (это ровно {@code usedValue != null}) считает DTO — как и
 * сегодня, только теперь считает один раз и в одном месте.
 *
 * <p>А вот {@link #label}, {@link #period}, {@link #unit} и {@link #accuracy}
 * ХРАНЯТСЯ, хотя выглядят справочником. Снимок — исторический документ:
 * подпись метрики базы менялась вместе с самой базой («Firestore · чтения» →
 * «PostgreSQL · размер базы»), и перерисовывать позапрошлогодний снимок
 * сегодняшним справочником значило бы врать о том, что тогда измеряли.
 *
 * <p>{@link #note} — одно поле вместо пары {@code note} и {@code error}:
 * и там, и там строка о том, почему числа нет, и читатель карточки не
 * отличает их всё равно.
 */
@Entity
@Table(name = "usage_metric_sample", schema = "v2")
@IdClass(UsageMetricSampleId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class UsageMetricSample {

    @Id
    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private Long snapshotId;

    /** Путь метрики в снимке: database.storage, vps.networkMonthly, mail.daily. */
    @Id
    @Column(name = "metric_key", nullable = false, updatable = false, length = 60)
    private String metricKey;

    @Column(nullable = false, updatable = false, length = 120)
    private String label;

    /** Пусто — источник значения не дал; это не ноль. */
    @Column(name = "used_value", updatable = false)
    private Long usedValue;

    /** Пусто — предела нет или он не задан. */
    @Column(name = "limit_value", updatable = false)
    private Long limitValue;

    /** day | month | total | current — набор закрыт ограничением базы. */
    @Column(nullable = false, updatable = false, length = 8)
    private String period;

    /** bytes либо штуки; в последнем случае единица названа в подписи. */
    @Column(nullable = false, updatable = false, length = 20)
    private String unit;

    /** Набор закрыт базой: exact, estimate, lower_bound, tracked, tracked_estimate, unavailable. */
    @Column(nullable = false, updatable = false, length = 20)
    private String accuracy;

    @Column(updatable = false, length = 400)
    private String note;

    @Column(updatable = false, length = 60)
    private String source;
}
