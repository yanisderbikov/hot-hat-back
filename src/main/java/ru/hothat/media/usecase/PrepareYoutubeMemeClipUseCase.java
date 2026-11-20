package ru.hothat.media.usecase;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import ru.hothat.config.ApiException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Prepares a short, user-requested YouTube clip on HOT-HAT's server.
 *
 * The website cannot silently open a background tab and call play()/pause()
 * across origins. For a YouTube video not embeddable through the official API,
 * a controlled server-provided MP4 avoids both focus theft and recording a
 * YouTube loading screen. Only public, downloadable, non-DRM media can work.
 *
 * Process execution is bounded by duration, concurrency and response size.
 * The process receives a validated 11-character ID, not an arbitrary URL.
 */
@Service
@Slf4j
public class PrepareYoutubeMemeClipUseCase {
    private static final int CLIP_SECONDS = 16;
    private static final long MAX_RESPONSE_BYTES = 24L * 1024 * 1024;
    private static final int PROCESS_TIMEOUT_SECONDS = 75;
    private static final Semaphore PARALLEL = new Semaphore(2, true);

    /**
     * Explicit opt-in Railway smoke test for one supplied public video ID.
     * Disabled unless the service variable is present. It does not expose
     * an unauthenticated downloader endpoint and never blocks app startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void optionalSmokeTest() {
        String videoId = System.getenv("HOT_HAT_YOUTUBE_IMPORT_SMOKE_ID");
        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) return;
        Thread.ofVirtual().name("youtube-clip-smoke").start(() -> {
            log.info("YouTube server import smoke test started id={}", videoId);
            try {
                byte[] bytes = run(videoId, 0.0);
                log.info("YouTube server import smoke test SUCCESS id={} bytes={}", videoId, bytes.length);
            } catch (RuntimeException error) {
                log.error("YouTube server import smoke test FAILED id={} detail={}", videoId, error.toString());
            }
        });
    }

    public byte[] run(String videoId, double requestedStart) {
        validate(videoId, requestedStart);
        if (!PARALLEL.tryAcquire()) throw ApiException.of("YOUTUBE_IMPORT_BUSY", 429);
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("hot-hat-yt-");
            Path processLog = workspace.resolve("extractor.log");
            List<String> command = buildCommand(videoId, requestedStart, workspace);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workspace.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(processLog.toFile());

            long started = System.nanoTime();
            Process process = builder.start();
            boolean exited;
            try {
                exited = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw ApiException.of("YOUTUBE_IMPORT_INTERRUPTED", 503);
            }
            if (!exited) {
                process.destroyForcibly();
                try { process.waitFor(2, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                throw ApiException.of("YOUTUBE_IMPORT_TIMEOUT", 504);
            }
            if (process.exitValue() != 0) {
                log.warn("YouTube clip import failed id={} exit={} detail={}",
                        videoId, process.exitValue(), tail(processLog));
                throw ApiException.of("YOUTUBE_IMPORT_UNAVAILABLE", 422);
            }
            Path mp4;
            try (Stream<Path> candidates = Files.list(workspace)) {
                mp4 = candidates.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".mp4"))
                        .findFirst()
                        .orElseThrow(() -> ApiException.of("YOUTUBE_IMPORT_NO_MP4", 422));
            }
            long bytes = Files.size(mp4);
            if (bytes < 2048 || bytes > MAX_RESPONSE_BYTES) {
                throw ApiException.of("YOUTUBE_IMPORT_SIZE_LIMIT", 413);
            }
            log.info("Prepared YouTube meme source id={} bytes={} elapsedMs={}",
                    videoId, bytes, Duration.ofNanos(System.nanoTime() - started).toMillis());
            return Files.readAllBytes(mp4);
        } catch (IOException e) {
            log.warn("YouTube clip import IO failure id={} detail={}", videoId, e.toString());
            throw ApiException.of("YOUTUBE_IMPORT_UNAVAILABLE", 503);
        } finally {
            if (workspace != null) deleteWorkspace(workspace);
            PARALLEL.release();
        }
    }

    static void validate(String videoId, double start) {
        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")
                || !Double.isFinite(start) || start < 0 || start > 7200) {
            throw ApiException.of("YOUTUBE_IMPORT_INVALID_SOURCE", 400);
        }
    }

    static List<String> buildCommand(String videoId, double start, Path directory) {
        validate(videoId, start);
        double end = start + CLIP_SECONDS;
        List<String> command = new ArrayList<>(List.of(
                "yt-dlp",
                "--ignore-config",
                "--no-playlist",
                "--force-ipv4",
                "--no-progress",
                "--no-part",
                "--socket-timeout", "10",
                "--retries", "1",
                "--fragment-retries", "1",
                "--concurrent-fragments", "1",
                "--js-runtimes", "node",
                "--download-sections", String.format(Locale.ROOT, "*%.3f-%.3f", start, end),
                "--force-keyframes-at-cuts",
                "--format", "b[ext=mp4][height<=720]/b[height<=720]/bv*[height<=720]+ba/b",
                "--merge-output-format", "mp4",
                "--recode-video", "mp4",
                "--limit-rate", "2M",
                "--output", directory.resolve("clip.%(ext)s").toString()
        ));
        command.add("https://www.youtube.com/watch?v=" + videoId);
        return command;
    }

    private static String tail(Path logFile) {
        try {
            String value = Files.readString(logFile);
            return value.substring(Math.max(0, value.length() - 600))
                    .replaceAll("[\\r\\n]+", " ");
        } catch (IOException e) {
            return "no extractor log";
        }
    }

    private static void deleteWorkspace(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); }
                catch (IOException e) { log.debug("Cannot clean YouTube temp file {}", p, e); }
            });
        } catch (IOException e) {
            log.debug("Cannot clean YouTube workspace {}", directory, e);
        }
    }
}
