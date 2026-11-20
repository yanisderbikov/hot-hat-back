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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
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
    private static final int MAX_RESULTS = 20;
    private static final int MAX_SEARCH_RESULTS = 81;
    private static final Duration TTL = Duration.ofMinutes(3);

    private final ObjectMapper json;
    private final Map<String, CachedSearch> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastRequests = new ConcurrentHashMap<>();

    public record VideoResult(String id, String title, Long durationMs, String thumbnail) {}
    private record CachedSearch(long until, List<VideoResult> items, boolean hasMore) {}

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @PathVariable @RoomId String roomId,
            @RequestParam("q") @Size(min = 1, max = 100) String text,
            @RequestParam(value = "offset", defaultValue = "0") @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(60) int offset,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                    ru.hothat.config.HotHatUser user) {
        String query = text.strip();
        if (query.isBlank()) return ResponseEntity.badRequest().build();
        long now = System.currentTimeMillis();
        String cacheKey = query.toLowerCase(java.util.Locale.ROOT) + ":" + offset;
        CachedSearch cached = cache.get(cacheKey);
        if (cached != null && cached.until() > now)
            return ResponseEntity.ok(Map.of("items", cached.items(), "hasMore", cached.hasMore()));
        if (lastRequests.size() > 10_000) lastRequests.clear();
        Long previous = lastRequests.get(user.uid());
        if (previous != null && now - previous < 650)
            return ResponseEntity.status(429).body(Map.of("error", "Поиск можно повторить через секунду."));
        // Rejected keystroke requests must not extend the rate-limit window.
        lastRequests.put(user.uid(), now);

        List<VideoResult> result = new ArrayList<>();
        boolean hasMore = false;
        final int limit = Math.min(MAX_SEARCH_RESULTS, offset + MAX_RESULTS + 1);
        Matcher linkedVideo = VIDEO_LINK.matcher(query);
        if (VIDEO_ID.matcher(query).matches() || linkedVideo.find()) {
            String id = VIDEO_ID.matcher(query).matches() ? query : linkedVideo.group(1);
            if (offset == 0) result.add(new VideoResult(id, "Видео YouTube", null, thumbnail(id)));
        } else {
            Process process = null;
            Path outputFile = null;
            try {
                // Never wait for yt-dlp before draining its output: JSON for many results
                // can exceed the OS pipe buffer and deadlock until the 22s timeout.
                // Redirect to a private temporary file instead of a piped stdout.
                outputFile = Files.createTempFile("hothat-youtube-search-", ".json");
                process = new ProcessBuilder(
                        "yt-dlp", "--flat-playlist", "--dump-single-json",
                        "--ignore-config", "--skip-download", "--no-warnings",
                        "--socket-timeout", "7",
                        "--retries", "1", "--extractor-retries", "1",
                        "--playlist-end", String.valueOf(limit),
                        "ytsearch" + limit + ":" + query)
                        .redirectErrorStream(true)
                        .redirectOutput(outputFile.toFile())
                        .start();
                boolean exited = process.waitFor(22, TimeUnit.SECONDS);
                if (!exited) {
                    process.destroyForcibly();
                    return ResponseEntity.status(504).body(Map.of(
                            "error", "YouTube долго отвечает. Попробуйте повторить поиск."));
                }
                if (Files.size(outputFile) > 4_000_000)
                    throw new IllegalStateException("YouTube search response too large");
                String output = Files.readString(outputFile, StandardCharsets.UTF_8);
                if (process.exitValue() != 0) {
                    return ResponseEntity.status(502).body(Map.of(
                            "error", "YouTube отклонил поисковый запрос. Повторите позже."));
                }
                List<VideoResult> all = parseResults(json, output, limit);
                if (all.isEmpty() && offset == 0) {
                    return ResponseEntity.status(502).body(Map.of(
                            "error", "YouTube не вернул результаты. Попробуйте другой запрос."));
                }
                hasMore = all.size() > offset + MAX_RESULTS;
                if (offset < all.size())
                    result.addAll(all.subList(offset, Math.min(offset + MAX_RESULTS, all.size())));
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                return ResponseEntity.status(502).body(Map.of(
                        "error", "Поиск YouTube временно недоступен. Можно вставить прямую ссылку."));
            } finally {
                if (process != null && process.isAlive()) process.destroyForcibly();
                if (outputFile != null) {
                    try { Files.deleteIfExists(outputFile); }
                    catch (java.io.IOException ignored) { /* ephemeral search result only */ }
                }
            }
        }
        List<VideoResult> items = List.copyOf(result);
        if (cache.size() > 250) cache.clear();
        cache.put(cacheKey, new CachedSearch(now + TTL.toMillis(), items, hasMore));
        return ResponseEntity.ok(Map.of("items", items, "hasMore", hasMore));
    }

    // Package-private for parsing regression tests; metadata only, no media downloads.
    static List<VideoResult> parseResults(ObjectMapper json, String output) throws java.io.IOException {
        return parseResults(json, output, MAX_RESULTS);
    }

    static List<VideoResult> parseResults(ObjectMapper json, String output, int maxResults) throws java.io.IOException {
        int begin = output.indexOf('{');
        int finish = output.lastIndexOf('}');
        if (begin < 0 || finish <= begin)
            throw new IllegalStateException("No YouTube search JSON");
        JsonNode entries = json.readTree(output.substring(begin, finish + 1)).path("entries");
        List<VideoResult> videos = new ArrayList<>();
        if (entries.isArray()) for (JsonNode entry : entries) {
            String id = entry.path("id").asText("");
            if (!VIDEO_ID.matcher(id).matches()) continue;
            String title = entry.path("title").asText("Видео YouTube");
            if (title.length() > 180) title = title.substring(0, 180);
            double seconds = entry.path("duration").asDouble(0);
            Long duration = Double.isFinite(seconds) && seconds > 0 && seconds < 24 * 3600
                    ? Math.round(seconds * 1000) : null;
            videos.add(new VideoResult(id, title, duration, thumbnail(id)));
            if (videos.size() >= Math.min(MAX_SEARCH_RESULTS, Math.max(1, maxResults))) break;
        }
        return videos;
    }

    private static String thumbnail(String videoId) {
        return "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
    }
}
