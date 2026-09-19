package ru.hothat.service.monitor.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import ru.hothat.config.HotHatProperties;
import ru.hothat.model.ops.*;
import ru.hothat.repository.GetterOps;
import ru.hothat.repository.SaverOps;
import ru.hothat.service.monitor.HostMetricsService;
import ru.hothat.service.monitor.MonitorService;
import ru.hothat.util.Json;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorServiceImpl implements MonitorService {

    private static final long GB = 1024L * 1024 * 1024;
    private static final long HOSTING_STORAGE_LIMIT = 10 * GB;
    private static final long HOSTING_TRANSFER_LIMIT = 10 * GB;
    private static final int AUTH_DAU_LIMIT = 3000;
    private static final int RESEND_DAILY_EMAILS = 100;
    private static final int RESEND_MONTHLY_EMAILS = 3000;
    /** Плановые снимки: четыре равномерных запуска в сутки. */
    private static final Set<Integer> SCHEDULED_HOURS = Set.of(5, 11, 17, 23);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final HostMetricsService hostMetricsService;
    private final GetterOps getterOps;
    private final SaverOps saverOps;
    private final HotHatProperties properties;
    private final WebClient.Builder webClientBuilder;
    private final DataSource dataSource;



    @Value("${monitor.vps-monthly-traffic-limit-gb:0}")
    private double vpsTrafficLimitGb;

    @Value("${report.resend-api-key:}")
    private String resendApiKey;

    @Value("${report.email:}")
    private String reportEmail;

    @Value("${report.from:}")
    private String reportFrom;

    @Value("${report.hour:23}")
    private int reportHour;

    @Value("${report.timezone:Asia/Yekaterinburg}")
    private String reportTimezone;

    // ───────────────────────────── метрики ─────────────────────────────

    private static Map<String, Object> metric(String label, Long used, Long limit, String period, String unit,
                                              String accuracy, Map<String, Object> extra) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("used", used);
        m.put("limit", limit);
        m.put("remaining", used == null || limit == null ? null : Math.max(0, limit - used));
        m.put("period", period);
        m.put("unit", unit);
        m.put("percent", used == null || limit == null || limit == 0 ? null
                : Math.max(0, (double) used / limit * 100));
        m.put("accuracy", accuracy);
        m.put("available", used != null);
        if (extra != null) {
            m.putAll(extra);
        }
        return m;
    }

    private static Map<String, Object> unavailable(String label, Long limit, String period, String unit, String note) {
        return metric(label, null, limit, period, unit, "unavailable", Map.of("note", note));
    }

    @Override
    public Map<String, Object> collect() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        Map<String, Object> vps = vpsMetrics();
        List<String> errors = new ArrayList<>();

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("collectedAt", Instant.now().toString());
        snapshot.put("localDate", now.format(DAY));
        snapshot.put("month", now.format(MONTH));
        snapshot.put("quotaDate", now.format(DAY));
        snapshot.put("quotaTimeZone", "UTC");
        snapshot.put("database", databaseSection());
        snapshot.put("hosting", hostingSection());
        snapshot.put("auth", unavailable("Авторизация · активные пользователи", (long) AUTH_DAU_LIMIT,
                "day", "пользователей",
                "DAU пока не считается; счётчик онлайна доступен в разделе комнат."));
        snapshot.put("vercel", unavailable("Бекенд · вызовы функций", null, "month", "вызовов",
                "Vercel больше не используется: бекенд работает на собственном сервере."));
        snapshot.put("vps", vps);
        snapshot.put("health", serviceHealth(vps));
        snapshot.put("mail", mailSection(now));
        snapshot.put("errors", errors);

        try {
            updateVpsTraffic(snapshot, vps, now);
        } catch (RuntimeException e) {
            errors.add("vps_traffic: " + e.getMessage());
        }
        return snapshot;
    }

    /**
     * Данные переехали в PostgreSQL, поэтому квоты Firestore больше не считаются.
     * Ключ сохранён: админка рисует по нему ту же строку таблицы.
     */
    private Map<String, Object> databaseSection() {
        String note = "Данные переехали в PostgreSQL: пооперационных квот больше нет.";
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("reads", unavailable("База · чтения", null, "day", "операций", note));
        section.put("writes", unavailable("База · записи", null, "day", "операций", note));
        section.put("deletes", unavailable("База · удаления", null, "day", "операций", note));
        section.put("storage", metric("PostgreSQL · размер базы", databaseSizeBytes(), null,
                "total", "bytes", "exact", Map.of("source", "pg_database_size")));
        return section;
    }

    private Long databaseSizeBytes() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select pg_database_size(current_database())")) {
            return rs.next() ? rs.getLong(1) : null;
        } catch (Exception e) {
            log.warn("Размер базы недоступен: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Ключ hosting в снимке сохранён: админка рисует по нему строку таблицы.
     * Пока фронтенд не переехал на свой сервер, считать тут нечего.
     */
    private Map<String, Object> hostingSection() {
        String note = "Раздача статики ещё не переведена на свой сервер.";
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("transfer", unavailable("Сайт · трафик", HOSTING_TRANSFER_LIMIT,
                "month", "bytes", note));
        section.put("storage", unavailable("Сайт · хранение", HOSTING_STORAGE_LIMIT,
                "total", "bytes", note));
        return section;
    }

    private Map<String, Object> mailSection(ZonedDateTime now) {
        long daily = getterOps.getMailCounter(now.format(DAY)).map(UsageMailCounter::getSent).orElse(0);
        long monthly = getterOps.getMailCounter(now.format(MONTH)).map(UsageMailCounter::getSent).orElse(0);
        Map<String, Object> mail = new LinkedHashMap<>();
        mail.put("configured", resendApiKey != null && !resendApiKey.isBlank());
        mail.put("daily", metric("Email · Resend", daily, (long) RESEND_DAILY_EMAILS, "day", "писем", "tracked", null));
        mail.put("monthly", metric("Email · Resend", monthly, (long) RESEND_MONTHLY_EMAILS, "month", "писем", "tracked", null));
        return mail;
    }

    // ───────────────────────────── VPS ─────────────────────────────

    private Map<String, Object> vpsMetrics() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!hostMetricsService.supported()) {
            result.put("available", false);
            result.put("error", "Метрики хоста доступны только на Linux (/proc)");
            return result;
        }
        Map<String, Object> data = hostMetricsService.collect();
        Map<String, Object> disk = Json.map(data.get("disk"));
        Map<String, Object> memory = Json.map(data.get("memory"));
        Map<String, Object> media = Json.map(data.get("media"));
        Map<String, Object> network = Json.map(data.get("network"));
        Map<String, Object> load = Json.map(data.get("load"));
        Map<String, Object> cpu = Json.map(data.get("cpu"));
        Map<String, Object> sockets = Json.map(data.get("sockets"));
        Long trafficLimit = vpsTrafficLimitGb > 0 ? Math.round(vpsTrafficLimitGb * GB) : null;

        result.put("available", true);
        result.put("raw", data);
        result.put("disk", metric("VPS · диск", nullable(disk.get("used")), nullable(disk.get("total")),
                "total", "bytes", "exact", null));
        result.put("memory", metric("VPS · RAM", nullable(memory.get("used")), nullable(memory.get("total")),
                "current", "bytes", "exact", null));
        result.put("mediaStorage", metric("VPS · мем-видео", nullable(media.get("bytes")),
                nullable(disk.get("total")), "total", "bytes", "exact", null));
        result.put("networkTotal", metric("VPS · сетевой трафик (счётчик ОС)", nullable(network.get("txBytes")),
                trafficLimit, "month", "bytes", "tracked",
                Map.of("note", trafficLimit != null ? "Лимит задан вручную."
                        : "Лимит трафика тарифа VPS пока не задан.")));
        Map<String, Object> loadRow = new LinkedHashMap<>();
        loadRow.put("label", "VPS · load average");
        loadRow.put("value", load.get("one"));
        loadRow.put("cores", cpu.get("cores"));
        result.put("load", loadRow);
        result.put("livekitSockets", sockets.get("livekit"));
        result.put("turnSockets", sockets.get("turn"));
        return result;
    }

    private static Long nullable(Object value) {
        return value == null ? null : Json.num(value);
    }

    /** Месячный трафик считается дельтами: счётчик ОС может обнулиться при перезагрузке. */
    private void updateVpsTraffic(Map<String, Object> snapshot, Map<String, Object> vps, ZonedDateTime now) {
        Map<String, Object> network = Json.map(Json.map(vps.get("raw")).get("network"));
        if (network.get("txBytes") == null) {
            return;
        }
        long current = Json.num(network.get("txBytes"));
        UsageMonitorState state = getterOps.getMonitorState("vpsNetwork")
                .orElseGet(() -> UsageMonitorState.builder().id("vpsNetwork").txBytes(current).build());
        long previous = state.getTxBytes() == null ? current : state.getTxBytes();
        long delta = current >= previous ? current - previous : 0;

        String monthKey = now.format(MONTH);
        UsageMonthly month = getterOps.getUsageMonth(monthKey)
                .orElseGet(() -> UsageMonthly.builder().month(monthKey).build());
        long monthlyBytes = month.getVpsNetworkTxBytes() + delta;
        month.setVpsNetworkTxBytes(monthlyBytes);
        saverOps.saveUsageMonth(month);

        state.setTxBytes(current);
        state.setRxBytes(nullable(network.get("rxBytes")));
        saverOps.saveMonitorState(state);

        Long limit = vpsTrafficLimitGb > 0 ? Math.round(vpsTrafficLimitGb * GB) : null;
        Map<String, Object> mutableVps = (Map<String, Object>) snapshot.get("vps");
        mutableVps.put("networkMonthly", metric("VPS · трафик за месяц", monthlyBytes, limit,
                "month", "bytes", "tracked", null));
    }

    // ───────────────────────── состояние сервисов ─────────────────────────

    private List<Map<String, Object>> serviceHealth(Map<String, Object> vps) {
        List<Map<String, Object>> health = new ArrayList<>();
        health.add(httpHealth("Сайт", properties.publicSiteUrl() + "/"));
        // Адрес живости теперь только в v2: прежний /api/health удалён.
        health.add(httpHealth("HOT-HAT API", properties.apiOrigin() + "/api/v2/app/health"));
        health.add(state("PostgreSQL", databaseSizeBytes() != null, "Запрос к базе выполняется"));
        boolean vpsReachable = Boolean.TRUE.equals(vps.get("available"));
        Map<String, Object> load = Json.map(Json.map(vps.get("raw")).get("load"));
        health.add(state("VPS", vpsReachable, vpsReachable
                ? String.format(Locale.ROOT, "Метрики получены · load %.2f", Json.dbl(load.get("one"), 0))
                : Json.str(vps.getOrDefault("error", "Нет связи"))));

        Map<String, Object> raw = Json.map(vps.get("raw"));
        Map<String, Object> services = Json.map(raw.get("services"));
        Map<String, Object> ports = Json.map(raw.get("ports"));
        health.add(vpsService("HOT-HAT back", services.get("back"), ports.get("back")));
        health.add(vpsService("LiveKit", services.get("livekit"), ports.get("livekit")));
        health.add(vpsService("TURN / coturn", services.get("turn"), ports.get("turn_tls")));
        health.add(vpsService("nginx", services.get("nginx"), ports.get("nginx_local")));
        health.add(vpsService("HAProxy", services.get("haproxy"), ports.get("haproxy_https")));

        boolean resendConfigured = resendApiKey != null && !resendApiKey.isBlank();
        Map<String, Object> email = state("Email-отчёты (Resend)", resendConfigured,
                resendConfigured ? "Ключ настроен" : "RESEND_API_KEY не настроен");
        if (!resendConfigured) {
            email.put("status", "warn");
        }
        health.add(email);
        return health;
    }

    private Map<String, Object> state(String label, boolean ok, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("status", ok ? "ok" : "down");
        row.put("ok", ok);
        row.put("checkedAt", Instant.now().toString());
        row.put("detail", detail);
        return row;
    }

    private Map<String, Object> vpsService(String label, Object service, Object port) {
        if (service == null && port == null) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", label);
            row.put("status", "unknown");
            row.put("ok", null);
            row.put("checkedAt", Instant.now().toString());
            row.put("detail", "Нет данных");
            return row;
        }
        Map<String, Object> serviceMap = Json.map(service);
        Map<String, Object> portMap = Json.map(port);
        boolean serviceOk = service == null || Json.bool(serviceMap.get("ok"));
        boolean portOk = port == null || Json.bool(portMap.get("listening"));
        List<String> details = new ArrayList<>();
        if (service != null) {
            details.add("systemd: " + Json.str(serviceMap.getOrDefault("state", "unknown")));
        }
        if (port != null) {
            details.add("порт " + Json.str(portMap.get("port")) + ": "
                    + (Json.bool(portMap.get("listening")) ? "слушает" : "не слушает"));
        }
        return state(label, serviceOk && portOk, String.join(" · ", details));
    }

    private Map<String, Object> httpHealth(String label, String url) {
        long started = System.currentTimeMillis();
        try {
            webClientBuilder.build().get().uri(url)
                    .header("User-Agent", "HOT-HAT-Monitor/1.0")
                    .retrieve().toBodilessEntity().block(Duration.ofSeconds(7));
            Map<String, Object> row = state(label, true, "Доступен");
            row.put("latencyMs", System.currentTimeMillis() - started);
            return row;
        } catch (Exception e) {
            Map<String, Object> row = state(label, false, Json.str(e.getMessage(), 200));
            row.put("latencyMs", System.currentTimeMillis() - started);
            return row;
        }
    }

    // ───────────────────────── снимки, письма, история ─────────────────────────

    @Override
    public boolean scheduledWindowAllowed() {
        return SCHEDULED_HOURS.contains(ZonedDateTime.now(ZoneId.of(reportTimezone)).getHour());
    }

    @Override
    @Transactional
    public Map<String, Object> collectAndPersist(boolean withNotifications) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        Map<String, Object> snapshot = collect();
        String date = now.format(DAY);
        UsageDaily day = getterOps.getUsageDay(date).orElseGet(() -> UsageDaily.builder().date(date).build());
        day.setLatest(snapshot);
        saverOps.saveUsageDay(day);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("snapshot", snapshot);
        if (withNotifications) {
            result.put("thresholdAlerts", thresholdAlerts(snapshot, now));
            result.put("dailyEmail", dailyReport(snapshot));
        }
        return result;
    }

    /** Одно письмо на метрику и период: повторная тревога по тому же порогу не шлётся. */
    private List<Map<String, Object>> thresholdAlerts(Map<String, Object> snapshot, ZonedDateTime now) {
        List<Map<String, Object>> sent = new ArrayList<>();
        for (Map<String, Object> m : flattenMetrics(snapshot)) {
            Object limit = m.get("limit");
            Object percent = m.get("percent");
            if (limit == null || percent == null || Json.dbl(percent, 0) < 50) {
                continue;
            }
            String metricKey = Json.str(m.get("label")).toLowerCase()
                    .replaceAll("[^a-zа-я0-9]+", "-");
            metricKey = metricKey.length() > 100 ? metricKey.substring(0, 100) : metricKey;
            String period = Json.str(m.get("period"));
            String periodKey = "month".equals(period) ? now.format(MONTH)
                    : "day".equals(period) ? now.format(DAY) : "total";
            String alertId = periodKey + "_" + metricKey + "_50";
            if (getterOps.getAlert(alertId).isPresent()) {
                continue;
            }
            UsageAlert alert = UsageAlert.builder().id(alertId).metric(m).threshold(50).status("pending").build();
            saverOps.saveAlert(alert);
            Map<String, Object> email = sendReportEmail(
                    String.format(Locale.ROOT, "HOT-HAT: %s использовано %.0f%%",
                            Json.str(m.get("label")), Json.dbl(percent, 0)),
                    snapshot, "threshold");
            alert.setStatus(Boolean.TRUE.equals(email.get("sent")) ? "sent" : "email_failed");
            alert.setEmail(email);
            alert.setUpdatedAt(Instant.now());
            saverOps.saveAlert(alert);
            sent.add(Map.of("metric", Json.str(m.get("label")), "email", email));
        }
        return sent;
    }

    private Map<String, Object> dailyReport(Map<String, Object> snapshot) {
        ZonedDateTime local = ZonedDateTime.now(ZoneId.of(reportTimezone));
        int hour = Math.max(0, Math.min(23, reportHour));
        if (local.getHour() != hour) {
            return null;
        }
        String date = local.format(DAY);
        if (getterOps.getReport(date).isPresent()) {
            return null;
        }
        UsageReport report = UsageReport.builder()
                .date(date).timeZone(reportTimezone).status("pending").snapshot(snapshot).build();
        saverOps.saveReport(report);
        Map<String, Object> email = sendReportEmail("HOT-HAT: расход ресурсов за " + date, snapshot, "daily");
        report.setStatus(Boolean.TRUE.equals(email.get("sent")) ? "sent" : "email_failed");
        report.setEmail(email);
        report.setUpdatedAt(Instant.now());
        saverOps.saveReport(report);
        return email;
    }

    private List<Map<String, Object>> flattenMetrics(Object node) {
        List<Map<String, Object>> out = new ArrayList<>();
        walkMetrics(node, out);
        return out;
    }

    private void walkMetrics(Object node, List<Map<String, Object>> out) {
        if (node instanceof Map<?, ?> raw) {
            Map<String, Object> map = Json.map(raw);
            if (map.containsKey("used") && map.containsKey("limit") && map.get("label") != null) {
                out.add(map);
                return;
            }
            map.values().forEach(value -> walkMetrics(value, out));
        } else if (node instanceof Collection<?> collection) {
            collection.forEach(value -> walkMetrics(value, out));
        }
    }

    private Map<String, Object> sendReportEmail(String subject, Map<String, Object> snapshot, String kind) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (resendApiKey == null || resendApiKey.isBlank()) {
            result.put("sent", false);
            result.put("reason", "RESEND_API_KEY не настроен");
            return result;
        }
        String to = reportEmail != null && !reportEmail.isBlank() ? reportEmail : properties.owner();
        List<String> lines = new ArrayList<>();
        lines.add("HOT-HAT · " + ("threshold".equals(kind) ? "предупреждение о расходе" : "ежедневный отчёт"));
        lines.add("Снимок: " + Json.str(snapshot.get("collectedAt")));
        lines.add("");
        flattenMetrics(snapshot).forEach(m -> lines.add(formatMetric(m)));
        Map<String, Object> vps = Json.map(snapshot.get("vps"));
        Map<String, Object> load = Json.map(vps.get("load"));
        if (!load.isEmpty()) {
            lines.add("VPS load: " + Json.str(load.get("value")) + "; CPU cores: " + Json.str(load.get("cores")));
        }
        if (vps.get("livekitSockets") != null) {
            lines.add("LiveKit sockets: " + Json.str(vps.get("livekitSockets")));
        }
        if (vps.get("turnSockets") != null) {
            lines.add("TURN sockets: " + Json.str(vps.get("turnSockets")));
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("from", reportFrom == null || reportFrom.isBlank()
                    ? "HOT-HAT Monitor <onboarding@resend.dev>" : reportFrom);
            body.put("to", List.of(to));
            body.put("subject", subject);
            body.put("text", String.join("\n", lines));
            Map<String, Object> response = Json.map(webClientBuilder.build().post()
                    .uri("https://api.resend.com/emails")
                    .header("Authorization", "Bearer " + resendApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(15)));
            bumpMailCounters();
            result.put("sent", true);
            result.put("id", response.get("id"));
            return result;
        } catch (Exception e) {
            result.put("sent", false);
            result.put("reason", Json.str(e.getMessage(), 200));
            return result;
        }
    }

    private void bumpMailCounters() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        for (String key : List.of(now.format(DAY), now.format(MONTH))) {
            UsageMailCounter counter = getterOps.getMailCounter(key)
                    .orElseGet(() -> UsageMailCounter.builder().periodKey(key).build());
            counter.setSent(counter.getSent() + 1);
            saverOps.saveMailCounter(counter);
        }
    }

    private static String formatMetric(Map<String, Object> m) {
        boolean bytes = "bytes".equals(Json.str(m.get("unit")));
        Object usedValue = m.get("used");
        Object limitValue = m.get("limit");
        String used = usedValue == null ? "—"
                : bytes ? formatBytes(Json.num(usedValue)) : String.valueOf(Math.round(Json.dbl(usedValue, 0)));
        String limit = limitValue == null ? (bytes ? "—" : "лимит не задан")
                : bytes ? formatBytes(Json.num(limitValue)) : String.valueOf(Math.round(Json.dbl(limitValue, 0)));
        String percent = m.get("percent") == null ? "—"
                : String.format(Locale.ROOT, "%.1f%%", Json.dbl(m.get("percent"), 0));
        return Json.str(m.get("label")) + ": " + used + " / " + limit + " (" + percent + ")";
    }

    private static String formatBytes(long value) {
        String[] units = {"Б", "КБ", "МБ", "ГБ", "ТБ"};
        double n = value;
        int i = 0;
        while (n >= 1024 && i < units.length - 1) {
            n /= 1024;
            i++;
        }
        return String.format(Locale.ROOT, "%." + (i >= 3 ? 2 : 1) + "f %s", n, units[i]);
    }

    @Override
    public Map<String, Object> history(String start, String end) {
        List<UsageDaily> days = getterOps.getUsageDays(start, end, 120);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (UsageDaily day : days) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", day.getDate());
            row.put("date", day.getDate());
            row.put("latest", day.getLatest());
            rows.add(row);
        }
        rows.sort(Comparator.comparing(row -> Json.str(row.get("date"))));

        Map<String, Object> latest = rows.isEmpty() ? null : Json.map(rows.get(rows.size() - 1).get("latest"));
        Map<String, Object> selected = null;
        String selectedDate = null;
        boolean selectedNoData = false;
        // Запрос одного дня («start == end») отдаёт снимок именно за этот день.
        if (start != null && !start.isBlank() && start.equals(end)) {
            selectedDate = start;
            selected = rows.stream()
                    .filter(row -> start.equals(Json.str(row.get("date"))))
                    .map(row -> Json.map(row.get("latest")))
                    .findFirst().orElse(null);
            selectedNoData = selected == null;
        }

        List<Map<String, Object>> alerts = new ArrayList<>();
        for (UsageAlert alert : getterOps.getRecentAlerts(50)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", alert.getId());
            row.put("metric", alert.getMetric());
            row.put("threshold", alert.getThreshold());
            row.put("status", alert.getStatus());
            row.put("email", alert.getEmail());
            row.put("createdAt", alert.getCreatedAt());
            alerts.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("latest", latest);
        result.put("selected", selected);
        result.put("selectedDate", selectedDate);
        result.put("selectedNoData", selectedNoData);
        result.put("days", rows);
        result.put("alerts", alerts);
        return result;
    }

    /**
     * Счётчик обращений к базе фронтенд шлёт агрегированно. Хранилище счётчика —
     * тот же VPS-сервис, что и раньше: он переживает перезапуск бекенда.
     */
}
