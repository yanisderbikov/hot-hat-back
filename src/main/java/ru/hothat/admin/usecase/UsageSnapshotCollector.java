package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.hothat.admin.domain.HealthProbe;
import ru.hothat.admin.domain.HostUnit;
import ru.hothat.admin.domain.MetricKey;
import ru.hothat.admin.domain.SnapshotAuthor;
import ru.hothat.admin.domain.ThresholdBreach;
import ru.hothat.admin.domain.UsageMetric;
import ru.hothat.admin.port.HostMetricsPort;
import ru.hothat.admin.store.DatabaseProbe;
import ru.hothat.admin.store.LivenessProbe;
import ru.hothat.admin.store.UsageMailer;
import ru.hothat.admin.store.UsageStore;
import ru.hothat.config.HotHatProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Сбор снимка расхода: общее тело двух сценариев.
 *
 * <p>Снимают трое — расписание, администратор кнопкой и агент мониторинга, — и
 * собирают они одно и то же. Различаются только последствия: письма о
 * превышении и ежедневный отчёт шлёт лишь плановый снимок, а ручной делает
 * ровно то, о чём просили. Раньше это была одна ветка с булевым аргументом
 * внутри движка, и «кто снял» не записывалось вовсе.
 *
 * <p>Своей транзакции здесь нет намеренно: сбор ходит по сети за состоянием
 * сайта, API и машины, и держать транзакцию открытой всё это время запрещено
 * (§7.2). Транзакционна каждая запись — снимок целиком, накопление трафика,
 * тревога, письмо, — и это объявлено у двери в таблицы.
 *
 * <p>Ни один сбойный источник не роняет снимок. Недоступная база даёт метрику
 * без значения и красную строку в сводке; сорванный пересчёт трафика пишет
 * причину в {@code collectError}. Снимок без половины чисел полезнее, чем
 * отсутствие снимка: по нему видно, что именно сломалось.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsageSnapshotCollector {

    private static final long GB = 1024L * 1024 * 1024;
    private static final long HOSTING_STORAGE_LIMIT = 10 * GB;
    private static final long HOSTING_TRANSFER_LIMIT = 10 * GB;
    private static final long AUTH_DAU_LIMIT = 3000;
    private static final long RESEND_DAILY_EMAILS = 100;
    private static final long RESEND_MONTHLY_EMAILS = 3000;

    /** Плановые снимки: четыре равномерных запуска в сутки. */
    private static final List<Integer> SCHEDULED_HOURS = List.of(5, 11, 17, 23);

    /** Пояс, в котором считаются дневные квоты. Он же едет в снимок. */
    private static final ZoneId QUOTA_ZONE = ZoneId.of("UTC");

    private static final String DATABASE_NOTE = "Данные переехали в PostgreSQL: пооперационных квот больше нет.";
    private static final String HOSTING_NOTE = "Раздача статики ещё не переведена на свой сервер.";
    private static final String AUTH_NOTE = "DAU пока не считается; счётчик онлайна доступен в разделе комнат.";
    private static final String COUNT_UNIT = "операций";

    private final HostMetricsPort host;
    private final DatabaseProbe database;
    private final LivenessProbe liveness;
    private final UsageStore store;
    private final UsageMailer mailer;
    private final UsageLetters letters;
    private final HotHatProperties properties;
    private final Clock clock;

    @Value("${monitor.vps-monthly-traffic-limit-gb:0}")
    private double vpsTrafficLimitGb;

    @Value("${report.hour:23}")
    private int reportHour;

    @Value("${report.timezone:Asia/Yekaterinburg}")
    private String reportTimezone;

    /** Разрешён ли плановый запуск в текущий час. */
    public boolean scheduledWindowAllowed() {
        return SCHEDULED_HOURS.contains(ZonedDateTime.now(clock.withZone(ZoneId.of(reportTimezone))).getHour());
    }

    /**
     * Снять снимок и сохранить его.
     *
     * @param byUid кто нажал кнопку; только у ручного снимка, у остальных null
     * @param notify слать ли письма о превышении и ежедневный отчёт
     */
    public Taken take(SnapshotAuthor author, String byUid, boolean notify) {
        Instant now = Instant.now(clock);
        LocalDate day = now.atZone(QUOTA_ZONE).toLocalDate();
        Optional<HostMetricsPort.HostSnapshot> machine = host.read();

        List<UsageMetric> metrics = new ArrayList<>();
        Optional<Long> databaseSize = database.sizeBytes();
        metrics.addAll(databaseMetrics(databaseSize));
        metrics.addAll(hostingMetrics());
        metrics.add(UsageMetric.unavailable(MetricKey.AUTH_ACTIVE_USERS,
                "Авторизация · активные пользователи", AUTH_DAU_LIMIT,
                UsageMetric.Period.DAY, "пользователей", AUTH_NOTE));

        String collectError = null;
        if (machine.isPresent()) {
            metrics.addAll(machineMetrics(machine.get()));
            try {
                metrics.add(monthlyTraffic(machine.get(), day));
            } catch (RuntimeException e) {
                // Пересчёт трафика — единственный источник, который может
                // сорваться сам по себе: он пишет в базу. Снимок от этого не
                // пропадает, но причина обязана в нём остаться.
                collectError = "vps_traffic: " + e.getMessage();
                log.warn("Трафик VPS не пересчитан: {}", e.getMessage());
            }
        }
        metrics.addAll(mailMetrics(day, now));

        UsageStore.Stored stored = store.record(new UsageStore.NewSnapshot(
                author,
                byUid,
                day,
                QUOTA_ZONE.getId(),
                machine.isPresent(),
                machine.isPresent() ? null : HostMetricsPort.UNSUPPORTED_REASON,
                machine.map(HostMetricsPort.HostSnapshot::loadAverage).orElse(null),
                machine.map(HostMetricsPort.HostSnapshot::cpuCores).orElse(null),
                machine.map(HostMetricsPort.HostSnapshot::livekitSockets).orElse(null),
                machine.map(HostMetricsPort.HostSnapshot::turnSockets).orElse(null),
                mailer.configured(),
                collectError,
                metrics,
                probes(machine, databaseSize)));

        if (!notify) {
            return new Taken(stored, 0, false);
        }
        return new Taken(stored, raiseAlerts(stored, metrics, day), sendDailyReport(stored));
    }

    // ───────────────────────────── метрики ─────────────────────────────

    /**
     * Три первые метрики базы всегда недоступны: пооперационных квот у своей
     * базы нет. Ключи оставлены, потому что по ним рисуется таблица истории,
     * где старые дни ещё несут числа Firestore.
     */
    private List<UsageMetric> databaseMetrics(Optional<Long> sizeBytes) {
        return List.of(
                UsageMetric.unavailable(MetricKey.DATABASE_READS, "База · чтения", null,
                        UsageMetric.Period.DAY, COUNT_UNIT, DATABASE_NOTE),
                UsageMetric.unavailable(MetricKey.DATABASE_WRITES, "База · записи", null,
                        UsageMetric.Period.DAY, COUNT_UNIT, DATABASE_NOTE),
                UsageMetric.unavailable(MetricKey.DATABASE_DELETES, "База · удаления", null,
                        UsageMetric.Period.DAY, COUNT_UNIT, DATABASE_NOTE),
                sizeBytes
                        .map(size -> UsageMetric.measured(MetricKey.DATABASE_STORAGE,
                                        "PostgreSQL · размер базы", size, null,
                                        UsageMetric.Period.TOTAL, UsageMetric.Accuracy.EXACT, UsageMetric.BYTES)
                                .from("pg_database_size"))
                        // Раньше метрика без числа всё равно называлась точной.
                        // Это ложь на экране, и база её теперь не примет.
                        .orElseGet(() -> UsageMetric.unavailable(MetricKey.DATABASE_STORAGE,
                                "PostgreSQL · размер базы", null,
                                UsageMetric.Period.TOTAL, UsageMetric.BYTES,
                                "База не ответила на запрос размера.")));
    }

    private List<UsageMetric> hostingMetrics() {
        return List.of(
                UsageMetric.unavailable(MetricKey.HOSTING_TRANSFER, "Сайт · трафик",
                        HOSTING_TRANSFER_LIMIT, UsageMetric.Period.MONTH, UsageMetric.BYTES, HOSTING_NOTE),
                UsageMetric.unavailable(MetricKey.HOSTING_STORAGE, "Сайт · хранение",
                        HOSTING_STORAGE_LIMIT, UsageMetric.Period.TOTAL, UsageMetric.BYTES, HOSTING_NOTE));
    }

    private List<UsageMetric> machineMetrics(HostMetricsPort.HostSnapshot machine) {
        Long trafficLimit = trafficLimitBytes();
        List<UsageMetric> metrics = new ArrayList<>(4);
        metrics.add(UsageMetric.measured(MetricKey.VPS_DISK, "VPS · диск",
                machine.disk().used(), machine.disk().total(),
                UsageMetric.Period.TOTAL, UsageMetric.Accuracy.EXACT, UsageMetric.BYTES));
        metrics.add(UsageMetric.measured(MetricKey.VPS_MEMORY, "VPS · RAM",
                machine.memory().used(), machine.memory().total(),
                UsageMetric.Period.CURRENT, UsageMetric.Accuracy.EXACT, UsageMetric.BYTES));
        metrics.add(machine.mediaBytes() == null
                ? UsageMetric.unavailable(MetricKey.VPS_MEDIA_STORAGE, "VPS · мем-видео",
                        machine.disk().total(), UsageMetric.Period.TOTAL, UsageMetric.BYTES,
                        "Папку с мем-видео прочитать не удалось.")
                : UsageMetric.measured(MetricKey.VPS_MEDIA_STORAGE, "VPS · мем-видео",
                        machine.mediaBytes(), machine.disk().total(),
                        UsageMetric.Period.TOTAL, UsageMetric.Accuracy.EXACT, UsageMetric.BYTES));
        metrics.add(machine.networkTxBytes() == null
                ? UsageMetric.unavailable(MetricKey.VPS_NETWORK_TOTAL,
                        "VPS · сетевой трафик (счётчик ОС)", trafficLimit,
                        UsageMetric.Period.MONTH, UsageMetric.BYTES, "Счётчик ОС не прочитан.")
                : UsageMetric.measured(MetricKey.VPS_NETWORK_TOTAL,
                                "VPS · сетевой трафик (счётчик ОС)", machine.networkTxBytes(), trafficLimit,
                                UsageMetric.Period.MONTH, UsageMetric.Accuracy.TRACKED, UsageMetric.BYTES)
                        .withNote(trafficLimit != null ? "Лимит задан вручную."
                                : "Лимит трафика тарифа VPS пока не задан."));
        return metrics;
    }

    /**
     * Месячный трафик копится дельтами: счётчик ОС обнуляется при перезагрузке
     * машины, и разность с прошлым показанием — единственное, чему можно верить.
     */
    private UsageMetric monthlyTraffic(HostMetricsPort.HostSnapshot machine, LocalDate day) {
        if (machine.networkTxBytes() == null) {
            return UsageMetric.unavailable(MetricKey.VPS_NETWORK_MONTHLY, "VPS · трафик за месяц",
                    trafficLimitBytes(), UsageMetric.Period.MONTH, UsageMetric.BYTES,
                    "Счётчик ОС не прочитан.");
        }
        long monthly = store.accumulateTraffic(machine.networkTxBytes(), machine.networkRxBytes(), day);
        return UsageMetric.measured(MetricKey.VPS_NETWORK_MONTHLY, "VPS · трафик за месяц",
                monthly, trafficLimitBytes(),
                UsageMetric.Period.MONTH, UsageMetric.Accuracy.TRACKED, UsageMetric.BYTES);
    }

    private List<UsageMetric> mailMetrics(LocalDate day, Instant now) {
        Instant dayStart = day.atStartOfDay(QUOTA_ZONE).toInstant();
        Instant monthStart = day.withDayOfMonth(1).atStartOfDay(QUOTA_ZONE).toInstant();
        UsageStore.MailCounts counts = store.mailCounts(dayStart, monthStart, now);
        return List.of(
                UsageMetric.measured(MetricKey.MAIL_DAILY, "Email · Resend",
                        counts.daily(), RESEND_DAILY_EMAILS,
                        UsageMetric.Period.DAY, UsageMetric.Accuracy.TRACKED, "писем"),
                UsageMetric.measured(MetricKey.MAIL_MONTHLY, "Email · Resend",
                        counts.monthly(), RESEND_MONTHLY_EMAILS,
                        UsageMetric.Period.MONTH, UsageMetric.Accuracy.TRACKED, "писем"));
    }

    private Long trafficLimitBytes() {
        return vpsTrafficLimitGb > 0 ? Math.round(vpsTrafficLimitGb * GB) : null;
    }

    // ─────────────────────── проверки живости ───────────────────────

    private List<HealthProbe> probes(Optional<HostMetricsPort.HostSnapshot> machine,
                                     Optional<Long> databaseSize) {
        List<HealthProbe> probes = new ArrayList<>();
        probes.add(liveness.ping(HealthProbe.SITE, "Сайт", properties.publicSiteUrl() + "/"));
        // Проба стучится по живому адресу живости: старый /api/health снесён
        // вместе с прежним слоем, и ссылка на него сделала бы «API не
        // отвечает» из обычного 404.
        probes.add(liveness.ping(HealthProbe.API, "HOT-HAT API",
                properties.apiOrigin() + "/api/v2/app/health"));
        probes.add(databaseSize.isPresent()
                ? HealthProbe.up(HealthProbe.POSTGRES, "PostgreSQL", "Запрос к базе выполняется")
                : HealthProbe.down(HealthProbe.POSTGRES, "PostgreSQL", "База не отвечает на запрос размера"));

        if (machine.isEmpty()) {
            probes.add(HealthProbe.down(HealthProbe.VPS, "VPS", HostMetricsPort.UNSUPPORTED_REASON));
            // Служб не видно не потому, что они упали, а потому, что смотреть
            // нечем. Это «неизвестно», и путать его с «не работает» нельзя.
            for (HostUnit unit : HostUnit.all()) {
                probes.add(HealthProbe.unknown(unit.probeKey(), unit.label(), "Нет данных"));
            }
        } else {
            HostMetricsPort.HostSnapshot snapshot = machine.get();
            probes.add(HealthProbe.up(HealthProbe.VPS, "VPS", String.format(Locale.ROOT,
                    "Метрики получены · load %.2f",
                    snapshot.loadAverage() == null ? 0 : snapshot.loadAverage())));
            for (HostMetricsPort.UnitState state : snapshot.units()) {
                probes.add(new HealthProbe(
                        state.unit().probeKey(),
                        state.unit().label(),
                        HealthProbe.Status.of(state.healthy()),
                        detail(state),
                        null));
            }
        }

        probes.add(mailer.configured()
                ? HealthProbe.up(HealthProbe.EMAIL, "Email-отчёты (Resend)", "Ключ настроен")
                // Предупреждение, а не отказ: сервис жив, письма — нет.
                : HealthProbe.warn(HealthProbe.EMAIL, "Email-отчёты (Resend)", "RESEND_API_KEY не настроен"));
        return probes;
    }

    private static String detail(HostMetricsPort.UnitState state) {
        List<String> parts = new ArrayList<>(2);
        parts.add("systemd: " + state.state());
        if (state.port() != null) {
            parts.add("порт " + state.port() + ": "
                    + (Boolean.TRUE.equals(state.listening()) ? "слушает" : "не слушает"));
        }
        return String.join(" · ", parts);
    }

    // ───────────────────────── письма ─────────────────────────

    /** Одно письмо на метрику и период: повторная тревога по тому же порогу не шлётся. */
    private int raiseAlerts(UsageStore.Stored stored, List<UsageMetric> metrics, LocalDate day) {
        int raised = 0;
        for (ThresholdBreach breach : ThresholdBreach.in(metrics, day)) {
            Optional<Long> alertId = store.raise(breach);
            if (alertId.isEmpty()) {
                continue;
            }
            UsageMailer.Sent sent = mailer.send(UsageStore.MAIL_ALERT,
                    letters.thresholdSubject(breach.metricLabel(), breach.percent()),
                    letters.threshold(stored));
            store.alertNotified(alertId.get(), sent.emailId(), sent.delivered());
            raised++;
        }
        return raised;
    }

    /** Ежедневный отчёт уходит в назначенный час и ровно один раз за день. */
    private boolean sendDailyReport(UsageStore.Stored stored) {
        ZonedDateTime local = ZonedDateTime.now(clock.withZone(ZoneId.of(reportTimezone)));
        if (local.getHour() != Math.max(0, Math.min(23, reportHour))) {
            return false;
        }
        LocalDate date = local.toLocalDate();
        if (!store.startReport(date, reportTimezone, stored.id())) {
            return false;
        }
        UsageMailer.Sent sent = mailer.send(UsageStore.MAIL_REPORT,
                letters.dailySubject(date.toString()), letters.daily(stored));
        store.reportSent(date, sent.emailId(), sent.delivered());
        return sent.delivered();
    }

    /** Что получилось: сам снимок, сколько тревог завели и ушёл ли отчёт. */
    public record Taken(UsageStore.Stored snapshot, int alertsRaised, boolean reportDelivered) {
    }
}
