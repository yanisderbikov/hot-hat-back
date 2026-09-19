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
 * Проверка живости одной службы в момент снимка.
 *
 * <p>Десять строк вместо массива объектов внутри jsonb: сайт, наш API,
 * PostgreSQL, сама машина, четыре службы на ней (back, LiveKit, coturn, nginx,
 * HAProxy) и настроенность почты.
 *
 * <p>Булева {@code ok} рядом со {@link #status} здесь нет: сегодня их два, и у
 * предупреждения {@code ok} равно true, а у неизвестного — null. Второе поле
 * выражало то же самое хуже. Состояний ровно четыре, они названы.
 *
 * <p>{@link #probeKey} — устойчивое имя проверки, а не её подпись. Сегодня
 * ключа нет вовсе, и строку опознают по русской подписи: переименование
 * подписи разрывало историю.
 */
@Entity
@Table(name = "service_health_probe", schema = "v2")
@IdClass(ServiceHealthProbeId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ServiceHealthProbe {

    /** Состояние службы; набор закрыт ограничением базы. */
    static final String OK = "ok";
    static final String WARN = "warn";
    static final String DOWN = "down";
    static final String UNKNOWN = "unknown";

    @Id
    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private Long snapshotId;

    /** site | api | postgres | vps | back | livekit | turn | nginx | haproxy | email. */
    @Id
    @Column(name = "probe_key", nullable = false, updatable = false, length = 40)
    private String probeKey;

    @Column(nullable = false, updatable = false, length = 60)
    private String label;

    @Builder.Default
    @Column(nullable = false, updatable = false, length = 8)
    private String status = UNKNOWN;

    @Column(updatable = false, length = 300)
    private String detail;

    /** Пусто у проверок, которые не ходят по сети. */
    @Column(name = "latency_ms", updatable = false)
    private Integer latencyMs;

    @Builder.Default
    @Column(name = "checked_at", nullable = false, updatable = false)
    private Instant checkedAt = Instant.now();
}
