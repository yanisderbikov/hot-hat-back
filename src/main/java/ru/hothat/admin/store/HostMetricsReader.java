package ru.hothat.admin.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Показания машины: /proc, systemd и слушающие порты.
 *
 * <p>Порт hot-hat-media-server.py: тот же набор метрик, но без отдельного
 * процесса, nginx перед ним и HMAC-секрета — бекенд живёт на той же машине и
 * читает /proc сам.
 *
 * <p>Читатель лежит у владельца раздела наблюдения, но отдаёт сырую карту:
 * разбор её в понятия консоли — дело {@link HostMetricsAdapter}, и разбор
 * этот один на всех. Раньше карту разбирали двое и расходились: у одного
 * свободное место на диске звалось {@code free}, у другого {@code available}.
 *
 * <p>Все сборщики намеренно не бросают исключений: раздел мониторинга должен
 * рисоваться, даже если ss не установлен или systemd недоступен.
 */
@Component
@Slf4j
public class HostMetricsReader {

    private static final Path MEMINFO = Paths.get("/proc/meminfo");
    private static final Path NET_DEV = Paths.get("/proc/net/dev");

    /** systemd-юниты, состояние которых показывает админка. */
    private static final Map<String, String> UNITS = Map.of(
            "back", "hot-hat-back",
            "livekit", "livekit",
            "turn", "coturn",
            "nginx", "nginx",
            "haproxy", "haproxy");

    @Value("${monitor.host.disk-root:/}")
    private String diskRoot;

    /**
     * Каталог, который раньше был хранилищем мем-видео. Сейчас видео лежат в S3,
     * поэтому по умолчанию считать нечего — путь задаётся, только если на машине
     * ещё остаются локальные файлы.
     */
    @Value("${monitor.host.media-root:}")
    private String mediaRoot;

    @Value("${server.port:8092}")
    private int backPort;

    /** false на не-Linux: /proc нет, и считать нечего (например, на macOS в разработке). */
    public boolean supported() {
        return Files.exists(MEMINFO);
    }

    /** Форма карты сохранена: её ключи разбирает переходник консоли. */
    public Map<String, Object> collect() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("service", "hot-hat-back");
        result.put("timestamp", System.currentTimeMillis());
        result.put("disk", disk());
        result.put("memory", memory());
        result.put("network", network());
        result.put("media", mediaFiles());
        result.put("load", load());
        result.put("cpu", Map.of("cores", Runtime.getRuntime().availableProcessors()));
        result.put("sockets", socketCounts());
        result.put("services", serviceStates());
        result.put("ports", listeningPorts());
        return result;
    }

    private Map<String, Object> disk() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            java.io.File root = Paths.get(diskRoot).toFile();
            long total = root.getTotalSpace();
            long free = root.getUsableSpace();
            result.put("total", total);
            result.put("used", Math.max(0, total - free));
            result.put("free", free);
        } catch (Exception e) {
            result.put("total", 0L);
            result.put("used", 0L);
            result.put("free", 0L);
        }
        return result;
    }

    private Map<String, Object> memory() {
        long total = 0;
        long available = 0;
        for (String line : readLines(MEMINFO)) {
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon);
            if (!key.equals("MemTotal") && !key.equals("MemAvailable")) {
                continue;
            }
            String[] parts = line.substring(colon + 1).trim().split("\\s+");
            long kb = parts.length > 0 ? parseLong(parts[0]) : 0;
            if (key.equals("MemTotal")) {
                total = kb * 1024;
            } else {
                available = kb * 1024;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("used", Math.max(0, total - available));
        result.put("available", available);
        return result;
    }

    /** Интерфейс lo пропускаем: локальный трафик к тарифу не относится. */
    private Map<String, Object> network() {
        long rx = 0;
        long tx = 0;
        List<String> lines = readLines(NET_DEV);
        for (int i = 2; i < lines.size(); i++) {
            String line = lines.get(i);
            int colon = line.indexOf(':');
            if (colon < 0 || line.substring(0, colon).trim().equals("lo")) {
                continue;
            }
            String[] parts = line.substring(colon + 1).trim().split("\\s+");
            if (parts.length >= 9) {
                rx += parseLong(parts[0]);
                tx += parseLong(parts[8]);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rxBytes", rx);
        result.put("txBytes", tx);
        return result;
    }

    private Map<String, Object> mediaFiles() {
        Map<String, Object> result = new LinkedHashMap<>();
        long count = 0;
        long bytes = 0;
        if (mediaRoot != null && !mediaRoot.isBlank()) {
            Path root = Paths.get(mediaRoot);
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path path : (Iterable<Path>) walk::iterator) {
                    if (!Files.isRegularFile(path) || path.getFileName().toString().startsWith(".")) {
                        continue;
                    }
                    count++;
                    try {
                        bytes += Files.size(path);
                    } catch (IOException ignored) {
                        // Файл мог исчезнуть между обходом и чтением размера.
                    }
                }
            } catch (Exception e) {
                log.debug("Каталог мем-видео {} не прочитан: {}", mediaRoot, e.getMessage());
            }
        }
        result.put("files", count);
        result.put("bytes", bytes);
        return result;
    }

    private Map<String, Object> load() {
        double one = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        Map<String, Object> result = new LinkedHashMap<>();
        // JVM отдаёт только одноминутное среднее; пятиминутное и пятнадцати-
        // минутное в админке не рисуются, поэтому за ними в /proc не идём.
        result.put("one", one < 0 ? 0.0 : one);
        result.put("five", 0.0);
        result.put("fifteen", 0.0);
        return result;
    }

    private Map<String, Object> socketCounts() {
        Map<String, Object> result = new LinkedHashMap<>();
        long livekit = 0;
        long turn = 0;
        for (String line : runCommand(List.of("ss", "-Htan"))) {
            if (line.contains(":7880") || line.contains(":7881")) {
                livekit++;
            }
            if (line.contains(":3478") || line.contains(":5349") || line.contains(":4443")) {
                turn++;
            }
        }
        result.put("livekit", livekit);
        result.put("turn", turn);
        return result;
    }

    private Map<String, Object> serviceStates() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : UNITS.entrySet()) {
            List<String> output = runCommand(List.of("systemctl", "is-active", entry.getValue()));
            String state = output.isEmpty() ? "unknown" : output.get(0).trim();
            if (state.isEmpty()) {
                state = "unknown";
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("unit", entry.getValue());
            row.put("state", state);
            row.put("ok", "active".equals(state));
            result.put(entry.getKey(), row);
        }
        return result;
    }

    private Map<String, Object> listeningPorts() {
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("back", backPort);
        expected.put("livekit", 7880);
        expected.put("turn_tls", 4443);
        expected.put("nginx_local", 8443);
        expected.put("haproxy_https", 443);

        Set<Integer> listening = new HashSet<>();
        for (String line : runCommand(List.of("ss", "-Hlnt"))) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 4) {
                continue;
            }
            String address = parts[3];
            int colon = address.lastIndexOf(':');
            if (colon >= 0) {
                listening.add((int) parseLong(address.substring(colon + 1)));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : expected.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("port", entry.getValue());
            row.put("listening", listening.contains(entry.getValue()));
            result.put(entry.getKey(), row);
        }
        return result;
    }

    private List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Двухсекундный потолок — тот же, что стоял у subprocess.run в питоне. */
    private List<String> runCommand(List<String> command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return List.of();
            }
            String output = new String(process.getInputStream().readAllBytes());
            return output.isBlank() ? List.of() : List.of(output.split("\\R"));
        } catch (Exception e) {
            return List.of();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
