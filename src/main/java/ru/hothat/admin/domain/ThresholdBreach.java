package ru.hothat.admin.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Метрика перевалила порог — повод для одного письма.
 *
 * <p>Чистое правило: на вход метрики и день снимка, на выходе список поводов.
 * Ни базы, ни часов внутри — день приходит снаружи, потому что «сегодня» у
 * квоты своё, в поясе квот, а не в поясе процесса.
 *
 * <p>Ключ повода машинный: вид периода, ключ периода, ключ метрики и порог.
 * Раньше он склеивался строкой, средним куском в неё шла РУССКАЯ ПОДПИСЬ
 * метрики в нижнем регистре, и переименование подписи заводило второй повод о
 * том же самом — то есть слало второе письмо. Подписи в ключе больше нет; она
 * едет рядом как исторический текст.
 */
public record ThresholdBreach(String periodKind, String periodKey, String metricKey,
                              String metricLabel, int threshold, double percent) {

    /** Единственный порог, о котором пишут письмо. */
    public static final int THRESHOLD_PERCENT = 50;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    /** Ключ периода «за всё время»: у такой метрики нет ни дня, ни месяца. */
    private static final String TOTAL_KEY = "total";

    /** Какие метрики снимка перевалили порог. Порядок — как пришли. */
    public static List<ThresholdBreach> in(Collection<UsageMetric> metrics, LocalDate day) {
        List<ThresholdBreach> breaches = new ArrayList<>();
        for (UsageMetric metric : metrics) {
            Double percent = metric.percent();
            if (percent == null || percent < THRESHOLD_PERCENT) {
                continue;
            }
            breaches.add(new ThresholdBreach(
                    periodKind(metric), periodKey(metric, day),
                    metric.key(), metric.label(), THRESHOLD_PERCENT, percent));
        }
        return breaches;
    }

    /**
     * Мгновенная метрика (занятая память) считается «за всё время»: база знает
     * только три вида периода, а держать четвёртый ради строки, у которой нет
     * ни дня, ни месяца, значило бы завести период без ключа.
     */
    private static String periodKind(UsageMetric metric) {
        return switch (metric.period()) {
            case DAY -> "day";
            case MONTH -> "month";
            case TOTAL, CURRENT -> "total";
        };
    }

    private static String periodKey(UsageMetric metric, LocalDate day) {
        return switch (metric.period()) {
            case DAY -> day.format(DAY);
            case MONTH -> day.format(MONTH);
            case TOTAL, CURRENT -> TOTAL_KEY;
        };
    }
}
