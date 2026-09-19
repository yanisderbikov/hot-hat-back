package ru.hothat.admin.store;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.admin.domain.HealthProbe;
import ru.hothat.admin.domain.SnapshotAuthor;
import ru.hothat.admin.domain.ThresholdBreach;
import ru.hothat.admin.domain.UsageMetric;
import ru.hothat.common.identity.LegacyIdBridge;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Единственная дверь области {@code admin} в таблицы расхода.
 *
 * <p>Наружу отдаёт записи и правила домена, а не сущности: {@code UsageSnapshot},
 * {@code UsageMetricSample} и остальные шесть не публичны. Иначе право
 * переписать позапрошлогодний снимок оказалось бы у всякого, кто его читает,
 * а снимок — исторический документ.
 *
 * <p>Транзакции стоят здесь, а не в сценарии, и это единственное отступление
 * от общего правила — названное вслух. Сбор снимка ходит по сети за
 * состоянием сайта, API и хранилища; держать транзакцию открытой всё это время
 * запрещено (§7.2). Поэтому сценарий транзакции не открывает, а каждая запись
 * — снимок с метриками и проверками, тревога, письмо — транзакционна сама по
 * себе и целиком.
 *
 * <p>Чтений в цикле здесь нет ни одного: метрики и проверки всей страницы
 * истории приезжают двумя запросами по списку идентификаторов.
 */
@Component
@RequiredArgsConstructor
public class UsageStore {

    /** Счётчик трафика ОС; сегодня он один, но ключ таблицы это допускает. */
    public static final String NETWORK_METER = "vpsNetwork";

    /** Виды писем; набор закрыт ограничением базы. */
    public static final String MAIL_ALERT = OutboundEmail.USAGE_ALERT;
    public static final String MAIL_REPORT = OutboundEmail.USAGE_REPORT;

    private final UsageSnapshots snapshots;
    private final UsageMetricSamples samples;
    private final ServiceHealthProbes probes;
    private final UsageAlerts alerts;
    private final OutboundEmails emails;
    private final TrafficMeters meters;
    private final TrafficPeriods periods;
    private final UsageReports reports;
    private final LegacyIdBridge ids;
    private final Clock clock;

    // ───────────────────────────── запись ─────────────────────────────

    /**
     * Сохранить снимок целиком: строку, её метрики и её проверки.
     *
     * <p>Три вставки одной транзакцией — это замена прежней записи в одну
     * строку с jsonb внутри. Метрики и проверки кладутся пачками, а не по
     * одной: их четырнадцать и десять на снимок.
     */
    @Transactional
    public Stored record(NewSnapshot fresh) {
        UsageSnapshot row = snapshots.save(UsageSnapshot.builder()
                .takenAt(Instant.now(clock))
                .takenBy(fresh.author().wire())
                // Ограничение базы требует ровно этого: человека называет
                // только ручной снимок, плановый и агентский — никогда.
                .takenByPlayerId(fresh.author().namesPerson() && fresh.byUid() != null
                        ? ids.playerId(fresh.byUid()) : null)
                .localDate(fresh.day())
                .quotaTimeZone(fresh.quotaTimeZone())
                .hostMetricsAvailable(fresh.hostMetricsAvailable())
                .hostMetricsError(fresh.hostMetricsError())
                .loadAverage(decimal(fresh.loadAverage()))
                .cpuCores(fresh.cpuCores() == null ? null : fresh.cpuCores().shortValue())
                // Колонки сокетов целые: их считают десятками, и Long здесь
                // только потому, что так их отдаёт читатель /proc.
                .livekitSockets(narrow(fresh.livekitSockets()))
                .turnSockets(narrow(fresh.turnSockets()))
                .mailConfigured(fresh.mailConfigured())
                .collectError(fresh.collectError())
                .build());

        List<UsageMetricSample> metricRows = new ArrayList<>(fresh.metrics().size());
        for (UsageMetric metric : fresh.metrics()) {
            metricRows.add(UsageMetricSample.builder()
                    .snapshotId(row.getId())
                    .metricKey(metric.key())
                    .label(metric.label())
                    .usedValue(metric.used())
                    .limitValue(metric.limit())
                    .period(metric.period().wire())
                    .unit(metric.unit())
                    .accuracy(metric.accuracy().wire())
                    .note(metric.note())
                    .source(metric.source())
                    .build());
        }
        samples.saveAll(metricRows);

        List<ServiceHealthProbe> probeRows = new ArrayList<>(fresh.probes().size());
        for (HealthProbe probe : fresh.probes()) {
            probeRows.add(ServiceHealthProbe.builder()
                    .snapshotId(row.getId())
                    .probeKey(probe.key())
                    .label(probe.label())
                    .status(probe.status().wire())
                    .detail(probe.detail())
                    .latencyMs(probe.latencyMs())
                    .checkedAt(row.getTakenAt())
                    .build());
        }
        probes.saveAll(probeRows);

        return view(row, fresh.metrics(), fresh.probes());
    }

    /**
     * Прибавить дельту счётчика ОС к дневному и месячному итогу.
     *
     * <p>Трафик считается ДЕЛЬТАМИ, потому что счётчик {@code /proc}
     * обнуляется при перезагрузке машины: если новое показание меньше
     * прошлого, дельта равна нулю, а не отрицательному числу.
     *
     * @return накопленный трафик за месяц
     */
    @Transactional
    public long accumulateTraffic(long txBytes, Long rxBytes, LocalDate day) {
        TrafficMeter meter = meters.findById(NETWORK_METER)
                .orElseGet(() -> TrafficMeter.builder().meterId(NETWORK_METER).txBytes(txBytes).build());
        long previous = meter.getTxBytes() == null ? txBytes : meter.getTxBytes();
        long delta = txBytes >= previous ? txBytes - previous : 0;
        meter.setTxBytes(txBytes);
        meter.setRxBytes(rxBytes);
        meter.setReadAt(Instant.now(clock));
        meters.save(meter);

        bump(TrafficPeriod.DAY, day.toString(), delta);
        return bump(TrafficPeriod.MONTH, day.toString().substring(0, 7), delta);
    }

    private long bump(String kind, String key, long delta) {
        TrafficPeriod period = periods.findById(new TrafficPeriodId(NETWORK_METER, kind, key))
                .orElseGet(() -> TrafficPeriod.builder()
                        .meterId(NETWORK_METER).periodKind(kind).periodKey(key).txBytes(0L).build());
        period.setTxBytes(period.getTxBytes() + delta);
        period.setUpdatedAt(Instant.now(clock));
        return periods.save(period).getTxBytes();
    }

    /**
     * Завести тревогу о превышении, если её ещё нет.
     *
     * @return идентификатор новой тревоги; пусто — о том же превышении уже
     *         писали, и второе письмо не нужно
     */
    @Transactional
    public Optional<Long> raise(ThresholdBreach breach) {
        short threshold = (short) breach.threshold();
        if (alerts.findByPeriodKindAndPeriodKeyAndMetricKeyAndThreshold(
                breach.periodKind(), breach.periodKey(), breach.metricKey(), threshold).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(alerts.save(UsageThresholdAlert.builder()
                .periodKind(breach.periodKind())
                .periodKey(breach.periodKey())
                .metricKey(breach.metricKey())
                .metricLabel(breach.metricLabel())
                .threshold(threshold)
                .percent(decimal(breach.percent()))
                .status(UsageThresholdAlert.PENDING)
                .createdAt(Instant.now(clock))
                .build()).getId());
    }

    /** Отметить, чем кончилась попытка отправить письмо о превышении. */
    @Transactional
    public void alertNotified(long alertId, long emailId, boolean sent) {
        alerts.findById(alertId).ifPresent(alert -> {
            alert.setEmailId(emailId);
            alert.setStatus(sent ? UsageThresholdAlert.NOTIFIED : UsageThresholdAlert.FAILED);
            alert.setUpdatedAt(Instant.now(clock));
            alerts.save(alert);
        });
    }

    /**
     * Начать ежедневный отчёт.
     *
     * @return {@code false} — отчёт за этот день уже готовили; второй раз не
     *         шлём
     */
    @Transactional
    public boolean startReport(LocalDate date, String timeZone, Long snapshotId) {
        if (reports.findById(date).isPresent()) {
            return false;
        }
        reports.save(UsageDailyReport.builder()
                .reportDate(date)
                .timeZone(timeZone)
                .snapshotId(snapshotId)
                .status(UsageDailyReport.PENDING)
                .createdAt(Instant.now(clock))
                .build());
        return true;
    }

    /** Отметить, чем кончилась попытка отправить ежедневный отчёт. */
    @Transactional
    public void reportSent(LocalDate date, long emailId, boolean sent) {
        reports.findById(date).ifPresent(report -> {
            report.setEmailId(emailId);
            report.setStatus(sent ? UsageDailyReport.SENT : UsageDailyReport.FAILED);
            report.setUpdatedAt(Instant.now(clock));
            reports.save(report);
        });
    }

    /** Завести письмо в журнале до попытки отправки: провал тоже история. */
    @Transactional
    public long queueEmail(String kind, String recipient, String subject) {
        return emails.save(OutboundEmail.builder()
                .kind(kind)
                .recipient(recipient)
                .subject(subject)
                .state(OutboundEmail.QUEUED)
                .requestedAt(Instant.now(clock))
                .build()).getId();
    }

    /** Письмо ушло. Ограничение базы требует времени отправки вместе с состоянием. */
    @Transactional
    public void emailSent(long emailId, String providerMessageId) {
        emails.findById(emailId).ifPresent(email -> {
            email.setState(OutboundEmail.SENT);
            email.setProviderMessageId(providerMessageId);
            email.setSentAt(Instant.now(clock));
            emails.save(email);
        });
    }

    /** Письмо не ушло. Причина обязательна: без неё база отвергнет строку. */
    @Transactional
    public void emailFailed(long emailId, String reason) {
        emails.findById(emailId).ifPresent(email -> {
            email.setState(OutboundEmail.FAILED);
            email.setFailureReason(reason == null || reason.isBlank() ? "Причина не названа" : reason);
            emails.save(email);
        });
    }

    // ───────────────────────────── чтение ─────────────────────────────

    /**
     * Страница истории: снимки за диапазон, свежие сверху.
     *
     * <p>Три запроса на любую длину страницы — снимки, их метрики, их
     * проверки. Пустые концы диапазона означают «всю историю».
     */
    @Transactional(readOnly = true)
    public List<Stored> history(LocalDate from, LocalDate to, int limit) {
        List<UsageSnapshot> rows = snapshots.findByLocalDateBetweenOrderByTakenAtDesc(
                from == null ? LocalDate.EPOCH : from,
                to == null ? LocalDate.of(9999, 12, 31) : to,
                PageRequest.of(0, Math.max(1, limit)));
        return assemble(rows);
    }

    /** Самый свежий снимок независимо от диапазона на экране. */
    @Transactional(readOnly = true)
    public Optional<Stored> latest() {
        return snapshots.findFirstByOrderByTakenAtDesc()
                .map(row -> assemble(List.of(row)).get(0));
    }

    /** Последние предупреждения о превышении порогов. */
    @Transactional(readOnly = true)
    public List<Alert> recentAlerts(int limit) {
        return alerts.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.max(1, limit))).stream()
                .map(row -> new Alert(
                        row.getId(),
                        row.getMetricLabel(),
                        row.getPercent() == null ? null : row.getPercent().doubleValue(),
                        row.getThreshold() == null ? 0 : row.getThreshold(),
                        row.getStatus(),
                        row.getCreatedAt() == null ? 0L : row.getCreatedAt().toEpochMilli()))
                .toList();
    }

    /**
     * Сколько писем ушло за сутки и за месяц.
     *
     * <p>Считается по журналу, а не по отдельному счётчику: счётчик был
     * выводимым и однажды разошёлся бы с журналом.
     */
    @Transactional(readOnly = true)
    public MailCounts mailCounts(Instant dayStart, Instant monthStart, Instant now) {
        return new MailCounts(
                emails.countByStateAndSentAtBetween(OutboundEmail.SENT, dayStart, now),
                emails.countByStateAndSentAtBetween(OutboundEmail.SENT, monthStart, now));
    }

    // ───────────────────────────── сборка ─────────────────────────────

    private List<Stored> assemble(List<UsageSnapshot> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> snapshotIds = rows.stream().map(UsageSnapshot::getId).toList();

        Map<Long, List<UsageMetric>> metricsBySnapshot = new LinkedHashMap<>();
        for (UsageMetricSample sample : samples.findBySnapshotIdIn(snapshotIds)) {
            metricsBySnapshot.computeIfAbsent(sample.getSnapshotId(), key -> new ArrayList<>())
                    .add(metric(sample));
        }
        Map<Long, List<HealthProbe>> probesBySnapshot = new LinkedHashMap<>();
        for (ServiceHealthProbe probe : probes.findBySnapshotIdIn(snapshotIds)) {
            probesBySnapshot.computeIfAbsent(probe.getSnapshotId(), key -> new ArrayList<>())
                    .add(probe(probe));
        }

        List<Stored> stored = new ArrayList<>(rows.size());
        for (UsageSnapshot row : rows) {
            List<HealthProbe> ordered = new ArrayList<>(
                    probesBySnapshot.getOrDefault(row.getId(), List.of()));
            // Порядок задаёт домен: у строк в базе своего порядка нет, а
            // человек читает сводку сверху вниз и ждёт её неизменной.
            ordered.sort(Comparator.comparingInt(probe -> {
                int index = HealthProbe.ORDER.indexOf(probe.key());
                return index < 0 ? HealthProbe.ORDER.size() : index;
            }));
            stored.add(view(row, metricsBySnapshot.getOrDefault(row.getId(), List.of()), ordered));
        }
        return stored;
    }

    private static Stored view(UsageSnapshot row, List<UsageMetric> metrics, List<HealthProbe> probes) {
        Map<String, UsageMetric> byKey = new LinkedHashMap<>();
        metrics.forEach(metric -> byKey.put(metric.key(), metric));
        return new Stored(
                row.getId(),
                row.getTakenAt() == null ? 0L : row.getTakenAt().toEpochMilli(),
                row.getLocalDate(),
                row.getQuotaTimeZone(),
                Boolean.TRUE.equals(row.getHostMetricsAvailable()),
                row.getHostMetricsError(),
                row.getLoadAverage() == null ? null : row.getLoadAverage().doubleValue(),
                row.getCpuCores() == null ? null : row.getCpuCores().intValue(),
                row.getLivekitSockets() == null ? null : row.getLivekitSockets().longValue(),
                row.getTurnSockets() == null ? null : row.getTurnSockets().longValue(),
                Boolean.TRUE.equals(row.getMailConfigured()),
                row.getCollectError(),
                byKey,
                List.copyOf(probes));
    }

    /**
     * Неизвестное слово в старой строке не роняет чтение всей истории: снимок
     * годовалой давности мог быть снят движком, который знал другие слова.
     */
    private static UsageMetric metric(UsageMetricSample sample) {
        return new UsageMetric(
                sample.getMetricKey(),
                sample.getLabel(),
                sample.getUsedValue(),
                sample.getLimitValue(),
                period(sample.getPeriod()),
                accuracy(sample.getAccuracy()),
                sample.getUnit(),
                sample.getNote(),
                sample.getSource());
    }

    private static HealthProbe probe(ServiceHealthProbe row) {
        return new HealthProbe(row.getProbeKey(), row.getLabel(), status(row.getStatus()),
                row.getDetail(), row.getLatencyMs());
    }

    private static UsageMetric.Period period(String wire) {
        for (UsageMetric.Period value : UsageMetric.Period.values()) {
            if (value.wire().equals(wire)) {
                return value;
            }
        }
        return UsageMetric.Period.CURRENT;
    }

    private static UsageMetric.Accuracy accuracy(String wire) {
        for (UsageMetric.Accuracy value : UsageMetric.Accuracy.values()) {
            if (value.wire().equals(wire)) {
                return value;
            }
        }
        return UsageMetric.Accuracy.UNAVAILABLE;
    }

    private static HealthProbe.Status status(String wire) {
        for (HealthProbe.Status value : HealthProbe.Status.values()) {
            if (value.wire().equals(wire)) {
                return value;
            }
        }
        return HealthProbe.Status.UNKNOWN;
    }

    private static Integer narrow(Long value) {
        return value == null ? null : (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }

    /** Два знака после запятой: столько же, сколько у колонки. */
    private static BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    // ───────────────────────────── записи ─────────────────────────────

    /** Снимок, который просят сохранить. */
    public record NewSnapshot(SnapshotAuthor author,
                              String byUid,
                              LocalDate day,
                              String quotaTimeZone,
                              boolean hostMetricsAvailable,
                              String hostMetricsError,
                              Double loadAverage,
                              Integer cpuCores,
                              Long livekitSockets,
                              Long turnSockets,
                              boolean mailConfigured,
                              String collectError,
                              List<UsageMetric> metrics,
                              List<HealthProbe> probes) {
    }

    /** Сохранённый снимок вместе с метриками по ключу и проверками по порядку. */
    public record Stored(long id,
                         long takenAtMs,
                         LocalDate day,
                         String quotaTimeZone,
                         boolean hostMetricsAvailable,
                         String hostMetricsError,
                         Double loadAverage,
                         Integer cpuCores,
                         Long livekitSockets,
                         Long turnSockets,
                         boolean mailConfigured,
                         String collectError,
                         Map<String, UsageMetric> metrics,
                         List<HealthProbe> probes) {

        /** Метрика по ключу; пусто — в этом снимке её не было вовсе. */
        public UsageMetric metric(String key) {
            return metrics.get(key);
        }
    }

    /** Тревога так, как её показывает лента админки. */
    public record Alert(long id, String metricLabel, Double percent, int threshold,
                        String status, long createdAtMs) {
    }

    /** Сколько писем ушло за сутки и за месяц. */
    public record MailCounts(long daily, long monthly) {
    }
}
