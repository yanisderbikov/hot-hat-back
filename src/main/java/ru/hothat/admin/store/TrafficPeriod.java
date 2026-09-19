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

import java.time.Instant;

/**
 * Накопленный трафик за период.
 *
 * <p>Заменяет {@code usage_monthly} и мёртвую
 * {@code usage_daily.vps_network_tx_bytes} одной сущностью с видом периода в
 * ключе: дневной и месячный счётчики — одно понятие с разной длиной шага, и
 * держать под них две таблицы значило бы дважды написать одно и то же
 * прибавление.
 *
 * <p>{@link #txBytes} — атомарный счётчик (§6.4). {@code @Version} нет
 * намеренно: прибавление дельты выражается одним оператором, и «последний
 * победил» здесь было бы как раз ошибкой, а оптимистичная блокировка —
 * лишним раундом.
 */
@Entity
@Table(name = "usage_traffic_period", schema = "v2")
@IdClass(TrafficPeriodId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class TrafficPeriod {

    /** Длина шага; набор закрыт ограничением базы. */
    static final String DAY = "day";
    static final String MONTH = "month";

    @Id
    @Column(name = "meter_id", nullable = false, updatable = false, length = 40)
    private String meterId;

    @Id
    @Column(name = "period_kind", nullable = false, updatable = false, length = 8)
    private String periodKind;

    /** 2026-09-06 либо 2026-09 — текстом, потому что шаг разный. */
    @Id
    @Column(name = "period_key", nullable = false, updatable = false, length = 10)
    private String periodKey;

    @Builder.Default
    @Column(name = "tx_bytes", nullable = false)
    private Long txBytes = 0L;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
