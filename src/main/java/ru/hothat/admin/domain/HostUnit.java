package ru.hothat.admin.domain;

import java.util.List;

/**
 * Служба на машине: как её зовут человеку, systemd и проверке порта.
 *
 * <p>Три имени вместо одного — не избыточность, а починка. Раньше юнит и его
 * порт сводил браузер по совпадению ключей, которого не было: {@code turn} в
 * карте служб отвечал ключу {@code turn_tls} в карте портов, и строка «TURN»
 * молча оставалась без порта. Здесь пара названа явно и один раз на всё
 * приложение: по этому же списку строится и карточка «состояние машины», и
 * проверки живости внутри снимка расхода.
 *
 * <p>{@link #probeKey()} совпадает с ключом systemd намеренно: в снимке
 * проверка опознаётся машинным именем, а подпись — исторический текст, который
 * можно переименовать, не разорвав историю.
 */
public enum HostUnit {

    BACK("HOT-HAT back", "back", "back"),
    LIVEKIT("LiveKit", "livekit", "livekit"),
    TURN("TURN / coturn", "turn", "turn_tls"),
    NGINX("nginx", "nginx", "nginx_local"),
    HAPROXY("HAProxy", "haproxy", "haproxy_https");

    /** Порядок фиксирован: человек читает один и тот же список в двух местах. */
    private static final List<HostUnit> ORDER = List.of(values());

    private final String label;
    private final String serviceKey;
    private final String portKey;

    HostUnit(String label, String serviceKey, String portKey) {
        this.label = label;
        this.serviceKey = serviceKey;
        this.portKey = portKey;
    }

    public static List<HostUnit> all() {
        return ORDER;
    }

    public String label() {
        return label;
    }

    public String serviceKey() {
        return serviceKey;
    }

    public String portKey() {
        return portKey;
    }

    /** Устойчивое имя проверки в снимке; по нему история сшивается через годы. */
    public String probeKey() {
        return serviceKey;
    }
}
