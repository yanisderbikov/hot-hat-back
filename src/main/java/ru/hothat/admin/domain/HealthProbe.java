package ru.hothat.admin.domain;

/**
 * Проверка живости одной службы в момент снимка.
 *
 * <p>Булевой {@code ok} рядом со {@link #status()} нет: раньше их было две, и у
 * предупреждения {@code ok} равнялся true, а у неизвестного — null. Второе
 * поле выражало то же самое хуже. Состояний ровно четыре, они названы.
 *
 * <p>{@link #key()} — устойчивое машинное имя. Раньше строку опознавали по
 * русской подписи, и переименование подписи разрывало историю.
 */
public record HealthProbe(String key, String label, Status status, String detail, Integer latencyMs) {

    /** Ключи проверок; набор закрыт ограничением базы вместе с длиной. */
    public static final String SITE = "site";
    public static final String API = "api";
    public static final String POSTGRES = "postgres";
    public static final String VPS = "vps";
    public static final String EMAIL = "email";

    /**
     * Порядок строк в сводке «здоровье систем».
     *
     * <p>Хранится здесь, а не выводится из запроса: у проверок в базе своего
     * порядка нет, а человек читает список сверху вниз и ждёт его неизменным —
     * сначала то, что снаружи (сайт, API), потом база, потом машина и её
     * службы, и последней почта.
     */
    public static final java.util.List<String> ORDER = order();

    private static java.util.List<String> order() {
        java.util.List<String> keys = new java.util.ArrayList<>();
        keys.add(SITE);
        keys.add(API);
        keys.add(POSTGRES);
        keys.add(VPS);
        HostUnit.all().forEach(unit -> keys.add(unit.probeKey()));
        keys.add(EMAIL);
        return java.util.List.copyOf(keys);
    }

    public static HealthProbe up(String key, String label, String detail) {
        return new HealthProbe(key, label, Status.OK, detail, null);
    }

    public static HealthProbe down(String key, String label, String detail) {
        return new HealthProbe(key, label, Status.DOWN, detail, null);
    }

    /** Работает, но что-то не настроено: почта без ключа отправителя. */
    public static HealthProbe warn(String key, String label, String detail) {
        return new HealthProbe(key, label, Status.WARN, detail, null);
    }

    /** Данных о службе нет вовсе — это не то же самое, что «не отвечает». */
    public static HealthProbe unknown(String key, String label, String detail) {
        return new HealthProbe(key, label, Status.UNKNOWN, detail, null);
    }

    /** Та же проверка с замеренным временем ответа. */
    public HealthProbe took(long millis) {
        return new HealthProbe(key, label, status, detail, (int) Math.max(0, Math.min(Integer.MAX_VALUE, millis)));
    }

    /** Состояние службы; набор закрыт ограничением базы. */
    public enum Status {
        OK("ok"), WARN("warn"), DOWN("down"), UNKNOWN("unknown");

        private final String wire;

        Status(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }

        public static Status of(boolean ok) {
            return ok ? OK : DOWN;
        }
    }
}
