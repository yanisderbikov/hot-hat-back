package ru.hothat.admin.usecase;

import org.springframework.stereotype.Component;
import ru.hothat.admin.api.dto.DatabaseUsageView;
import ru.hothat.admin.api.dto.HostingUsageView;
import ru.hothat.admin.api.dto.MailUsageView;
import ru.hothat.admin.api.dto.MetricAccuracy;
import ru.hothat.admin.api.dto.MetricPeriod;
import ru.hothat.admin.api.dto.ServiceHealthStatus;
import ru.hothat.admin.api.dto.ServiceHealthView;
import ru.hothat.admin.api.dto.UsageAlertView;
import ru.hothat.admin.api.dto.UsageMetricView;
import ru.hothat.admin.api.dto.UsageSnapshotView;
import ru.hothat.admin.api.dto.VpsUsageView;
import ru.hothat.admin.domain.HealthProbe;
import ru.hothat.admin.domain.MetricKey;
import ru.hothat.admin.domain.UsageMetric;
import ru.hothat.admin.port.HostMetricsPort;
import ru.hothat.admin.store.UsageStore;

import java.util.List;

/**
 * Хранимый снимок в ответ администратору.
 *
 * <p>Собирается из строк, а не из jsonb: снимок распался на строку, четырнадцать
 * метрик и десять проверок, и «пересобрать его обратно» — это ровно то, что
 * здесь происходит. Форма ответа при этом не изменилась ни на поле.
 *
 * <p>Выводимого в базе нет: {@code remaining}, {@code percent} и
 * {@code available} считает домен по ходу сборки — как и прежде, только теперь
 * в одном месте и по одной формуле.
 *
 * <p>Отсутствие метрики и метрика без значения — разные вещи, и обе выражены.
 * Метрики нет вовсе (снимок сняли, когда её ещё не собирали) — поле {@code null};
 * метрика есть, но источник не дал числа — объект с {@code available: false}.
 */
@Component
public class UsageSnapshotMapper {

    /**
     * Состояния тревоги на проводе.
     *
     * <p>В базе они машинные ({@code notified}, {@code failed}), а клиенту
     * едут прежние слова: форма ответа зафиксирована спецификацией, и менять
     * набор значений заодно с переездом хранилища значило бы сломать её ради
     * внутреннего переименования.
     */
    private static final String WIRE_SENT = "sent";
    private static final String WIRE_EMAIL_FAILED = "email_failed";
    private static final String WIRE_PENDING = "pending";

    /** Снимок целиком; {@code null} — снимка нет. */
    public UsageSnapshotView snapshot(UsageStore.Stored stored) {
        if (stored == null) {
            return null;
        }
        String day = stored.day() == null ? null : stored.day().toString();
        return new UsageSnapshotView(
                stored.takenAtMs(),
                day,
                day == null ? null : day.substring(0, 7),
                stored.quotaTimeZone(),
                new DatabaseUsageView(
                        metric(stored.metric(MetricKey.DATABASE_READS)),
                        metric(stored.metric(MetricKey.DATABASE_WRITES)),
                        metric(stored.metric(MetricKey.DATABASE_DELETES)),
                        metric(stored.metric(MetricKey.DATABASE_STORAGE))),
                new HostingUsageView(
                        metric(stored.metric(MetricKey.HOSTING_TRANSFER)),
                        metric(stored.metric(MetricKey.HOSTING_STORAGE))),
                metric(stored.metric(MetricKey.AUTH_ACTIVE_USERS)),
                vps(stored),
                new MailUsageView(
                        stored.mailConfigured(),
                        metric(stored.metric(MetricKey.MAIL_DAILY)),
                        metric(stored.metric(MetricKey.MAIL_MONTHLY))),
                stored.probes().stream().map(probe -> health(probe, stored.takenAtMs())).toList(),
                // Список из нуля или одной строки: источник, способный не
                // собраться, сегодня ровно один — пересчёт трафика VPS.
                stored.collectError() == null ? List.of() : List.of(stored.collectError()));
    }

    /** Строка расхода; {@code null} — раздела в снимке не было вовсе. */
    public UsageMetricView metric(UsageMetric metric) {
        if (metric == null) {
            return null;
        }
        return new UsageMetricView(
                metric.label(),
                metric.used(),
                metric.limit(),
                metric.remaining(),
                metric.percent(),
                MetricPeriod.fromWire(metric.period().wire()),
                metric.unit(),
                MetricAccuracy.fromWire(metric.accuracy().wire()),
                metric.available(),
                metric.note(),
                metric.source());
    }

    /** Предупреждение о превышении порога. */
    public UsageAlertView alert(UsageStore.Alert alert) {
        return new UsageAlertView(
                String.valueOf(alert.id()),
                alert.metricLabel(),
                alert.percent(),
                alert.threshold(),
                wireStatus(alert.status()),
                alert.createdAtMs());
    }

    private VpsUsageView vps(UsageStore.Stored stored) {
        boolean available = stored.hostMetricsAvailable();
        return new VpsUsageView(
                available,
                available ? null
                        : stored.hostMetricsError() == null
                                ? HostMetricsPort.UNSUPPORTED_REASON : stored.hostMetricsError(),
                metric(stored.metric(MetricKey.VPS_DISK)),
                metric(stored.metric(MetricKey.VPS_MEMORY)),
                metric(stored.metric(MetricKey.VPS_MEDIA_STORAGE)),
                metric(stored.metric(MetricKey.VPS_NETWORK_TOTAL)),
                metric(stored.metric(MetricKey.VPS_NETWORK_MONTHLY)),
                stored.loadAverage(),
                stored.cpuCores(),
                stored.livekitSockets(),
                stored.turnSockets());
    }

    /**
     * Время проверки — время снимка. Проверки идут внутри одного сбора и в
     * базу пишутся с той же отметкой; отдельная колонка на строку означала бы
     * десять почти одинаковых значений.
     */
    private static ServiceHealthView health(HealthProbe probe, long checkedAtMs) {
        return new ServiceHealthView(
                probe.label(),
                ServiceHealthStatus.fromWire(probe.status().wire()),
                checkedAtMs,
                probe.detail(),
                probe.latencyMs() == null ? null : probe.latencyMs().longValue());
    }

    private static String wireStatus(String stored) {
        return switch (stored == null ? "" : stored) {
            case "notified" -> WIRE_SENT;
            case "failed" -> WIRE_EMAIL_FAILED;
            default -> WIRE_PENDING;
        };
    }
}
