package ru.hothat.game.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Search YouTube without downloading, caching or storing video files.
 *
 * <p>Uses the yt-dlp search extractor already bundled in the Railway runtime.
 * Only title/id/duration and official YouTube thumbnails are returned. Actual
 * clips are fetched by every player's browser from YouTube itself on fire.
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v2/game/{roomId}/youtube-memes")
@PreAuthorize("@gameAuthz.isPlayer(#roomId)")
public class YoutubeMemeSearchController {

    private static final Pattern VIDEO_ID = Pattern.compile("^[a-zA-Z0-9_-]{11}$");
    private static final Pattern VIDEO_LINK = Pattern.compile(
            "(?:youtu\\.be/|youtube\\.com/(?:watch\\?[^#]*?v=|shorts/|embed/))([a-zA-Z0-9_-]{11})");
    private static final int MAX_RESULTS = 12;
    private static final Duration TTL = Duration.ofMinutes(3);

    private final ObjectMapper json;
    private final Map<String, CachedSearch> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastRequests = new ConcurrentHashMap<>();

    public record VideoResult(String id, String title, Long durationMs, String thumbnail) {}
    private record CachedSearch(long until, List<VideoResult> items) {}

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @PathVariable @RoomId String roomId,
            @RequestParam("q") @Size(min = 1, max = 100) String text,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                    ru.hothat.config.HotHatUser user) {
        String query = text.strip();
        if (query.isBlank()) return ResponseEntity.badRequest().build();
        long now = System.currentTimeMillis();
        CachedSearch cached = cache.get(query.toLowerCase(java.util.Locale.ROOT));
        if (cached != null && cached.until() > now) return ResponseEntity.ok(Map.of("items", cached.items()));
        if (lastRequests.size() > 10_000) lastRequests.clear();
        Long previous = lastRequests.put(user.uid(), now);
        if (previous != null && now - previous < 900)
            return ResponseEntity.status(429).body(Map.of("error", "Поиск можно повторить через секунду."));

        List<VideoResult> result = new ArrayList<>();
        Matcher linkedVideo = VIDEO_LINK.matcher(query);
        if (VIDEO_ID.matcher(query).matches() || linkedVideo.find()) {
            String id = VIDEO_ID.matcher(query).matches() ? query : linkedVideo.group(1);
            result.add(new VideoResult(id, "Видео YouTube", null, thumbnail(id)));
        } else {
            Process process = null;
            try {
                // Each argument is passed literally; never invoke through a shell.
                process = new ProcessBuilder(
                        "yt-dlp", "--flat-playlist", "--dump-single-json",
                        "--skip-download", "--no-warnings", "--socket-timeout", "7",
                        "--playlist-end", String.valueOf(MAX_RESULTS),
                        "ytsearch" + MAX_RESULTS + ":" + query)
                        .redirectErrorStream(true).start();
                boolean exited = process.waitFor(22, TimeUnit.SECONDS);
                if (!exited) {
                    process.destroyForcibly();
                    return ResponseEntity.status(504).body(Map.of("error", "YouTube долго отвечает. Повторите поиск."));
                }
                try (InputStream input = process.getInputStream()) {
                    byte[] data = input.readNBytes(400_000);
                    String output = new String(data, StandardCharsets.UTF_8);
                    // Extractor may prepend warning text. Locate JSON root.
                    int first = output.indexOf('{');
                    if (first < 0) throw new IllegalStateException("No YouTube search response");
                    JsonNode entries = json.readTree(output.substring(first)).path("entries");
                    if (entries.isArray()) for (JsonNode entry : entries) {
                        String id = entry.path("id").asText("");
                        if (!VIDEO_ID.matcher(id).matches()) continue;
                        String title = entry.path("title").asText("Видео YouTube");
                        if (title.length() > 180) title = title.substring(0, 180);
                        double seconds = entry.path("duration").asDouble(0);
                        Long duration = seconds > 0 && seconds < 24 * 3600
                                ? (long) (seconds * 1000) : null;
                        result.add(new VideoResult(id, title, duration, thumbnail(id)));
                        if (result.size() >= MAX_RESULTS) break;
                    }
                }
                if (result.isEmpty()) {
                    return ResponseEntity.status(502).body(Map.of(
                            "error", "YouTube не вернул результаты поиска. Можно вставить прямую ссылку на ролик."));
                }
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                return ResponseEntity.status(502).body(Map.of(
                        "error", "Поиск YouTube временно недоступен. Можно вставить прямую ссылку."));
            } finally {
                if (process != null && process.isAlive()) process.destroyForcibly();
            }
        }
        List<VideoResult> items = List.copyOf(result);
        if (cache.size() > 250) cache.clear();
        cache.put(query.toLowerCase(java.util.Locale.ROOT), new CachedSearch(now + TTL.toMillis(), items));
        return ResponseEntity.ok(Map.of("items", items));
    }

    private static String thumbnail(String videoId) {
        return "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
    }
}
