package ru.hothat.admin.store;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.admin.domain.HostUnit;
import ru.hothat.admin.port.HostMetricsPort;
import ru.hothat.util.Json;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Переходник к читателю {@code /proc}.
 *
 * <p>Разбор сырой карты живёт здесь и только здесь. Раньше её разбирали двое —
 * карточка машины и сборщик снимка, — и расходились: у одного диск считался
 * по ключу {@code free}, у другого по {@code available}, а служба и её порт
 * сводились по несовпадающим именам. Теперь ключи названы один раз.
 */
@Component
@RequiredArgsConstructor
public class HostMetricsAdapter implements HostMetricsPort {

    private final HostMetricsReader reader;
    private final Clock clock;

    @Override
    public Optional<HostSnapshot> read() {
        if (!reader.supported()) {
            return Optional.empty();
        }
        Map<String, Object> raw = reader.collect();
        Map<String, Object> network = Json.map(raw.get("network"));
        Map<String, Object> load = Json.map(raw.get("load"));
        Map<String, Object> cpu = Json.map(raw.get("cpu"));
        Map<String, Object> sockets = Json.map(raw.get("sockets"));
        return Optional.of(new HostSnapshot(
                (long) Json.num(raw.get("timestamp"), clock.millis()),
                bytes(Json.map(raw.get("disk"))),
                bytes(Json.map(raw.get("memory"))),
                nullable(network.get("rxBytes")),
                nullable(network.get("txBytes")),
                nullable(Json.map(raw.get("media")).get("bytes")),
                load.get("one") == null ? null : Json.dbl(load.get("one"), 0),
                cpu.get("cores") == null ? null : (int) Json.num(cpu.get("cores")),
                nullable(sockets.get("livekit")),
                nullable(sockets.get("turn")),
                units(Json.map(raw.get("services")), Json.map(raw.get("ports")))));
    }

    /**
     * Свободное место читается по двум разным ключам — диск зовёт его
     * {@code free}, память {@code available}, — а если нет ни того, ни другого,
     * выводится из разницы. Вычитание последним: оно приблизительно.
     */
    private static Bytes bytes(Map<String, Object> section) {
        long total = Json.num(section.get("total"));
        long used = Json.num(section.get("used"));
        long free = section.get("free") != null ? Json.num(section.get("free"))
                : section.get("available") != null ? Json.num(section.get("available"))
                : Math.max(0, total - used);
        return new Bytes(used, total, free);
    }

    private static List<UnitState> units(Map<String, Object> services, Map<String, Object> ports) {
        List<UnitState> states = new ArrayList<>(HostUnit.all().size());
        for (HostUnit unit : HostUnit.all()) {
            Map<String, Object> service = Json.map(services.get(unit.serviceKey()));
            Map<String, Object> port = Json.map(ports.get(unit.portKey()));
            states.add(new UnitState(
                    unit,
                    Json.str(service.getOrDefault("unit", unit.serviceKey())),
                    service.get("state") == null ? "unknown" : Json.str(service.get("state")),
                    Json.bool(service.get("ok")),
                    port.get("port") == null ? null : (int) Json.num(port.get("port")),
                    port.get("listening") == null ? null : Json.bool(port.get("listening"))));
        }
        return states;
    }

    private static Long nullable(Object value) {
        return value == null ? null : Json.num(value);
    }
}
