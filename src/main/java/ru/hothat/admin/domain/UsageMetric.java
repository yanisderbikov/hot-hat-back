package ru.hothat.admin.domain;

/**
 * Одна строка расхода: сколько занято из сколького.
 *
 * <p>Чистое правило без Spring и без базы. Выводимого в записи нет:
 * {@link #remaining()}, {@link #percent()} и {@link #available()} считаются, а
 * не хранятся, — ровно поэтому они не могут разойтись с {@link #used()}.
 *
 * <p>Инвариант «нет значения ⇔ недостоверна» проверяется здесь, в конструкторе,
 * а не только ограничением базы. Причина простая: метрика, у которой нет числа,
 * но написано {@code exact}, — это ложь на экране администратора, и узнавать о
 * ней от PostgreSQL на записи снимка поздно.
 */
public record UsageMetric(String key,
                          String label,
                          Long used,
                          Long limit,
                          Period period,
                          Accuracy accuracy,
                          String unit,
                          String note,
                          String source) {

    /** Единица «байты»; всё остальное — штуки, и они названы в подписи. */
    public static final String BYTES = "bytes";

    public UsageMetric {
        if ((used == null) != (accuracy == Accuracy.UNAVAILABLE)) {
            throw new IllegalArgumentException(
                    "Метрика " + key + ": значение и достоверность спорят друг с другом");
        }
        if (used != null && used < 0) {
            throw new IllegalArgumentException("Метрика " + key + ": отрицательный расход");
        }
    }

    /** Измеренная метрика. */
    public static UsageMetric measured(String key, String label, long used, Long limit,
                                       Period period, Accuracy accuracy, String unit) {
        return new UsageMetric(key, label, used, limit, period, accuracy, unit, null, null);
    }

    /** Метрика, которой источник не дал значения: предел известен, числа нет. */
    public static UsageMetric unavailable(String key, String label, Long limit,
                                          Period period, String unit, String note) {
        return new UsageMetric(key, label, null, limit, period, Accuracy.UNAVAILABLE, unit, note, null);
    }

    /** Та же метрика с пояснением: почему она такая, какая есть. */
    public UsageMetric withNote(String text) {
        return new UsageMetric(key, label, used, limit, period, accuracy, unit, text, source);
    }

    /** Та же метрика с названным источником значения. */
    public UsageMetric from(String origin) {
        return new UsageMetric(key, label, used, limit, period, accuracy, unit, note, origin);
    }

    /** Сколько осталось; пусто — нет значения либо нет предела. */
    public Long remaining() {
        return used == null || limit == null ? null : Math.max(0, limit - used);
    }

    /** Доля израсходованного в процентах; пусто — делить не на что. */
    public Double percent() {
        if (used == null || limit == null || limit == 0) {
            return null;
        }
        return Math.max(0, (double) used / limit * 100);
    }

    /** Значение есть. Ровно {@code used != null}; клиент ветвится по этому полю. */
    public boolean available() {
        return used != null;
    }

    /** За какой отрезок посчитана метрика; набор закрыт ограничением базы. */
    public enum Period {
        DAY("day"), MONTH("month"), TOTAL("total"), CURRENT("current");

        private final String wire;

        Period(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }
    }

    /** Насколько можно верить числу; набор закрыт ограничением базы. */
    public enum Accuracy {
        EXACT("exact"),
        ESTIMATE("estimate"),
        LOWER_BOUND("lower_bound"),
        TRACKED("tracked"),
        TRACKED_ESTIMATE("tracked_estimate"),
        UNAVAILABLE("unavailable");

        private final String wire;

        Accuracy(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }
    }
}
