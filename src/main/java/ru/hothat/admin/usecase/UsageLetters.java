package ru.hothat.admin.usecase;

import org.springframework.stereotype.Component;
import ru.hothat.admin.domain.UsageMetric;
import ru.hothat.admin.store.UsageStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Текст писем о расходе.
 *
 * <p>Отдельно от сборщика снимка потому, что это единственная часть, которую
 * читает человек, а не программа: подписи, единицы и порядок строк меняются от
 * вкуса, а сбор — нет.
 *
 * <p>Байты переводятся в килобайты и гигабайты здесь и только здесь. В самом
 * снимке они остаются байтами: письмо — представление, а метрика — измерение,
 * и округлять её при записи значило бы терять точность навсегда.
 */
@Component
public class UsageLetters {

    private static final String[] UNITS = {"Б", "КБ", "МБ", "ГБ", "ТБ"};

    /** Письмо о превышении порога: заголовок и весь снимок строками. */
    public List<String> threshold(UsageStore.Stored snapshot) {
        return letter("предупреждение о расходе", snapshot);
    }

    /** Ежедневный отчёт: тот же снимок, другой заголовок. */
    public List<String> daily(UsageStore.Stored snapshot) {
        return letter("ежедневный отчёт", snapshot);
    }

    /** Тема письма о превышении. Подпись метрики — та, что была в снимке. */
    public String thresholdSubject(String metricLabel, double percent) {
        return String.format(Locale.ROOT, "HOT-HAT: %s использовано %.0f%%", metricLabel, percent);
    }

    public String dailySubject(String day) {
        return "HOT-HAT: расход ресурсов за " + day;
    }

    private List<String> letter(String kind, UsageStore.Stored snapshot) {
        List<String> lines = new ArrayList<>();
        lines.add("HOT-HAT · " + kind);
        lines.add("Снимок: " + Instant.ofEpochMilli(snapshot.takenAtMs()));
        lines.add("");
        snapshot.metrics().values().forEach(metric -> lines.add(line(metric)));
        if (snapshot.loadAverage() != null) {
            lines.add("VPS load: " + snapshot.loadAverage() + "; CPU cores: " + snapshot.cpuCores());
        }
        if (snapshot.livekitSockets() != null) {
            lines.add("LiveKit sockets: " + snapshot.livekitSockets());
        }
        if (snapshot.turnSockets() != null) {
            lines.add("TURN sockets: " + snapshot.turnSockets());
        }
        if (snapshot.collectError() != null) {
            lines.add("Не собралось: " + snapshot.collectError());
        }
        return lines;
    }

    private static String line(UsageMetric metric) {
        boolean bytes = UsageMetric.BYTES.equals(metric.unit());
        String used = metric.used() == null ? "—"
                : bytes ? formatBytes(metric.used()) : String.valueOf(metric.used());
        String limit = metric.limit() == null ? (bytes ? "—" : "лимит не задан")
                : bytes ? formatBytes(metric.limit()) : String.valueOf(metric.limit());
        String percent = metric.percent() == null ? "—"
                : String.format(Locale.ROOT, "%.1f%%", metric.percent());
        return metric.label() + ": " + used + " / " + limit + " (" + percent + ")";
    }

    private static String formatBytes(long value) {
        double left = value;
        int unit = 0;
        while (left >= 1024 && unit < UNITS.length - 1) {
            left /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, "%." + (unit >= 3 ? 2 : 1) + "f %s", left, UNITS[unit]);
    }
}
