package ru.hothat.admin.store;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Один снимок расхода внешних сервисов и своей машины.
 *
 * <p>Сегодня снимок — строка {@code usage_daily} с ключом-датой и одним jsonb
 * внутри. Из этого следуют три беды сразу. Снимок за день ровно один, а
 * собираются они четыре раза в сутки (5:00, 11:00, 17:00 и 23:00 UTC) — три из
 * четырёх затирают друг друга. По метрике нельзя ни отобрать, ни
 * отсортировать. И «кто снял» не записано вовсе, хотя снимают трое: расписание,
 * администратор кнопкой «Проверить сейчас» и агент мониторинга.
 *
 * <p>Здесь остались только те поля снимка, которые не являются ни метрикой,
 * ни проверкой живости: метрики — {@link UsageMetricSample}, проверки —
 * {@link ServiceHealthProbe}.
 *
 * <p>Месяц не хранится: он выводится из {@link #localDate}. Generated-колонкой
 * его не сделать — {@code to_char} по дате не IMMUTABLE, — а вторая колонка с
 * тем же смыслом однажды разошлась бы с первой.
 *
 * <p>{@link #collectError} — колонка, а не список. Сегодня в снимке лежит
 * массив строк, но кладёт в него строку ровно один источник — пересчёт трафика
 * VPS. Второй источник получит своё имя в своей колонке; список молча потерял
 * бы, кто именно упал.
 */
@Entity
@Table(name = "usage_snapshot", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class UsageSnapshot {

    /** По чьей воле снят снимок; набор закрыт ограничением базы. */
    static final String SCHEDULE = "schedule";
    static final String ADMIN = "admin";
    static final String AGENT = "agent";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @Column(name = "taken_at", nullable = false, updatable = false)
    private Instant takenAt = Instant.now();

    @Builder.Default
    @Column(name = "taken_by", nullable = false, updatable = false, length = 16)
    private String takenBy = SCHEDULE;

    /** Кто нажал «Проверить сейчас»; пусто у планового и агентского снимка. */
    @Column(name = "taken_by_player_id", updatable = false)
    private UUID takenByPlayerId;

    @Column(name = "local_date", nullable = false, updatable = false)
    private LocalDate localDate;

    @Builder.Default
    @Column(name = "quota_time_zone", nullable = false, updatable = false, length = 60)
    private String quotaTimeZone = "UTC";

    /**
     * Метрики машины собираются только на Linux, из /proc. На другой системе
     * снимок берётся, но раздел VPS в нём пуст, и это надо отличать от нуля.
     */
    @Builder.Default
    @Column(name = "host_metrics_available", nullable = false, updatable = false)
    private Boolean hostMetricsAvailable = false;

    @Column(name = "host_metrics_error", updatable = false, length = 200)
    private String hostMetricsError;

    /**
     * Три значения раздела VPS, которые не метрики: у них нет ни предела, ни
     * периода, ни достоверности, и картой метрики они притворялись зря.
     */
    @Column(name = "load_average", updatable = false, precision = 6, scale = 2)
    private BigDecimal loadAverage;

    @Column(name = "cpu_cores", updatable = false)
    private Short cpuCores;

    @Column(name = "livekit_sockets", updatable = false)
    private Integer livekitSockets;

    @Column(name = "turn_sockets", updatable = false)
    private Integer turnSockets;

    /** Настроен ли отправитель писем на момент снимка. */
    @Builder.Default
    @Column(name = "mail_configured", nullable = false, updatable = false)
    private Boolean mailConfigured = false;

    @Column(name = "collect_error", updatable = false, length = 400)
    private String collectError;
}
