package ru.hothat.game.usecase;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.hothat.config.ApiException;
import ru.hothat.game.domain.weapon.MemeMedia;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * SCRUM-58: short, room-scoped MP4 clips supplied by a player.
 * No YouTube extraction, S3, library entries, user cookies or durable files.
 * Railway currently runs ONE backend replica; if scaled, replace this store
 * with a room-scoped, shared, expiring object store before adding replicas.
 */
@Service
public class EphemeralMemeClipStore {
    public static final long TTL_MS = 10 * 60_000L;
    public static final int MAX_UPLOAD_BYTES = 18_000_000;
    public static final int MAX_MP4_BYTES = 7_500_000;
    private static final int MAX_CLIPS = 24;
    private static final long MAX_MEMORY_BYTES = 48_000_000;
    private final Map<String, Clip> clips = new ConcurrentHashMap<>();
    private final Semaphore converters = new Semaphore(2);

    public record Clip(String id, String roomId, String ownerUid, String title,
                       long durationMs, long expiresAtMs, byte[] data) {}

    public static boolean isLocalId(String id) {
        return id != null && id.matches("^local:[0-9a-f]{32}$");
    }

    public Clip prepare(String roomId, String ownerUid, MultipartFile source,
                        double startSeconds, String requestedTitle) {
        if (roomId == null || roomId.isBlank() || ownerUid == null || ownerUid.isBlank())
            throw ApiException.of("MEME_INVALID", 400);
        if (source == null || source.isEmpty() || source.getSize() > MAX_UPLOAD_BYTES
                || !Double.isFinite(startSeconds) || startSeconds < 0 || startSeconds > 7200)
            throw ApiException.of("LOCAL_MEME_INVALID_SOURCE", 400);
        if (!converters.tryAcquire()) throw ApiException.of("LOCAL_MEME_CONVERSION_BUSY", 429);

        Path input = null, output = null, log = null;
        try {
            byte[] upload = source.getBytes();
            if (upload.length < 12 || upload.length > MAX_UPLOAD_BYTES
                    || upload[4] != 'f' || upload[5] != 't' || upload[6] != 'y' || upload[7] != 'p')
                throw ApiException.of("LOCAL_MEME_MP4_REQUIRED", 415);
            input = Files.createTempFile("hh-source-", ".mp4");
            output = Files.createTempFile("hh-short-", ".mp4");
            log = Files.createTempFile("hh-encode-", ".log");
            Files.write(input, upload);
            // No shell. Source is local upload, not an arbitrary remote URL.
            // Decode exactly the selected time interval; keep audio if present.
            List<String> command = List.of(
                    "ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                    "-ss", String.format(Locale.ROOT, "%.3f", startSeconds),
                    "-i", input.toString(), "-t", "10",
                    "-vf", "scale=w=854:h=480:force_original_aspect_ratio=decrease,format=yuv420p",
                    "-r", "24", "-c:v", "libx264", "-preset", "veryfast",
                    "-crf", "27", "-maxrate", "1400k", "-bufsize", "2800k",
                    "-c:a", "aac", "-b:a", "96k", "-movflags", "+faststart",
                    "-f", "mp4", output.toString());
            Process process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(log.toFile()).start();
            if (!process.waitFor(45, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw ApiException.of("LOCAL_MEME_CONVERSION_TIMEOUT", 422);
            }
            if (process.exitValue() != 0 || Files.size(output) < 2048)
                throw ApiException.of("LOCAL_MEME_CONVERSION_FAILED", 422);
            if (Files.size(output) > MAX_MP4_BYTES)
                throw ApiException.of("LOCAL_MEME_TOO_LARGE", 413);
            long durationMs = probeDuration(output);
            if (durationMs < 400 || durationMs > 10_200)
                throw ApiException.of("LOCAL_MEME_INVALID_DURATION", 422);

            String title = requestedTitle == null ? "" : requestedTitle.strip();
            if (title.isBlank()) title = "MP4-мем";
            if (title.length() > 90) title = title.substring(0, 90);
            long now = System.currentTimeMillis();
            String id = "local:" + UUID.randomUUID().toString().replace("-", "");
            Clip clip = new Clip(id, roomId, ownerUid, title,
                    Math.min(10_000, durationMs), now + TTL_MS, Files.readAllBytes(output));
            synchronized (clips) {
                evictExpired(now);
                clips.put(id, clip);
                evictOverflow();
            }
            return clip;
        } catch (ApiException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw ApiException.of("LOCAL_MEME_CONVERSION_FAILED", 422);
        } catch (Exception exception) {
            throw ApiException.of("LOCAL_MEME_CONVERSION_FAILED", 422);
        } finally {
            converters.release();
            deleteQuietly(input);
            deleteQuietly(output);
            deleteQuietly(log);
        }
    }

    private static long probeDuration(Path mp4) throws Exception {
        Process process = new ProcessBuilder("ffprobe", "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", mp4.toString())
                .redirectErrorStream(true).start();
        // Output is a single number; read before wait to avoid a blocked pipe.
        String output = new String(process.getInputStream().readNBytes(120),
                java.nio.charset.StandardCharsets.UTF_8).strip();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("ffprobe timeout");
        }
        if (process.exitValue() != 0) throw new IllegalStateException("ffprobe failed");
        double seconds = Double.parseDouble(output);
        if (!Double.isFinite(seconds)) throw new IllegalStateException("invalid duration");
        return Math.round(seconds * 1000);
    }

    public Clip find(String roomId, String id) {
        if (!isLocalId(id)) throw ApiException.of("LOCAL_MEME_NOT_FOUND", 404);
        Clip clip = clips.get(id);
        if (clip == null || !clip.roomId().equals(roomId)
                || clip.expiresAtMs() <= System.currentTimeMillis())
            throw ApiException.of("LOCAL_MEME_NOT_FOUND", 404);
        return clip;
    }

    public MemeMedia mediaForShot(String roomId, String ownerUid, String id) {
        Clip clip = find(roomId, id);
        // Only the uploader can spend a shot with their own temporary source.
        if (!clip.ownerUid().equals(ownerUid))
            throw ApiException.of("LOCAL_MEME_NOT_OWNED", 403);
        return new MemeMedia(clip.id(), clip.title(), clip.durationMs(), "", "", "",
                "", "ephemeral-mp4");
    }

    private void evictExpired(long now) {
        clips.entrySet().removeIf(entry -> entry.getValue().expiresAtMs() <= now);
    }

    private void evictOverflow() {
        long total = clips.values().stream().mapToLong(c -> c.data().length).sum();
        if (clips.size() <= MAX_CLIPS && total <= MAX_MEMORY_BYTES) return;
        List<Clip> byExpiry = clips.values().stream()
                .sorted(Comparator.comparingLong(Clip::expiresAtMs)).toList();
        for (Clip clip : byExpiry) {
            if (clips.size() <= MAX_CLIPS && total <= MAX_MEMORY_BYTES) break;
            if (clips.remove(clip.id(), clip)) total -= clip.data().length;
        }
    }

    private static void deleteQuietly(Path file) {
        if (file == null) return;
        try { Files.deleteIfExists(file); } catch (Exception ignored) { }
    }
}
