package ru.hothat.testbot.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Four GenAPI stages, with no retries of *paid* POSTs: Kling video editing,
 * TTS, Chatterbox voice transfer, and lip synchronization. A room gets one
 * paid experiment; any duplicate POST returns its existing task.
 *
 * This pilot is intentionally in memory; production needs durable queue state,
 * media storage and explicit economy limits before exposure to normal players.
 */
@Service
public class NeuroReplacementPilotService {
    private static final Logger LOG = LoggerFactory.getLogger(NeuroReplacementPilotService.class);
    private static final URI GENAPI = URI.create("https://api.gen-api.ru/api/v1/");
    private static final int MAX_VIDEO_BYTES = 12 * 1024 * 1024;
    private static final int MAX_VOICE_BYTES = 3 * 1024 * 1024;
    private static final int MAX_RESULT_BYTES = 40 * 1024 * 1024;
    private static final String SPOKEN_TEXT = "А вы всё ещё кипятите? Тогда мы идём к вам! А ты, сука, не бойся ночиииии!";
    // Match the successfully tested GenAPI website configuration: Kling O3,
    // video-to-video edit, ten seconds, PRO, source audio disabled.
    private static final String VIDEO_MODEL_ID = "kling-video-o3";
    private static final String VIDEO_PROMPT =
            "Edit the supplied webcam video, preserving the same person, face, hair, clothes, " +
            "lighting, room, furniture and camera framing. Keep one continuous static webcam shot. " +
            "0–3 seconds: the person looks into the camera and naturally speaks. " +
            "3–6 seconds: clearly raises the right hand in front of the chest and gives a big, " +
            "unmistakable thumbs-up toward the webcam, thumb pointing up and other fingers folded, " +
            "holding the gesture for one second. " +
            "6–10 seconds: lowers the hand and continues speaking naturally with an unobstructed face. " +
            "Do not change identity or background. No cuts, no camera movements, no other people, " +
            "no captions and no added objects. Ten seconds total.";

    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(25))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ExecutorService executor = Executors.newCachedThreadPool(task -> {
        Thread worker = new Thread(task, "neuro-replacement-pilot");
        worker.setDaemon(true);
        return worker;
    });
    private final Map<String, Job> byRoom = new ConcurrentHashMap<>();
    private JdbcTemplate jdbc;

    @Autowired(required = false)
    public void setJdbcTemplate(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Persist references before billable requests and after each stage. */
    private void persist(Job job) {
        if (jdbc == null) return; // unit tests without a Spring context
        try {
            jdbc.update("""
                INSERT INTO neuro_replacement_job
                 (job_id,room_id,owner_uid,state,stage,provider_requests,final_video_url,error_message,updated_at)
                VALUES (?,?,?,?,?,CAST(? AS jsonb),?,?,now())
                ON CONFLICT (room_id) DO UPDATE SET
                 state=EXCLUDED.state,stage=EXCLUDED.stage,
                 provider_requests=EXCLUDED.provider_requests,
                 final_video_url=EXCLUDED.final_video_url,
                 error_message=EXCLUDED.error_message,updated_at=now()
                WHERE neuro_replacement_job.job_id=EXCLUDED.job_id
                """,job.id,job.roomId,job.ownerUid,job.state,job.stage,
                json.writeValueAsString(new LinkedHashMap<>(job.providerRequests)),
                job.finalVideo,job.error);
        } catch (Exception e) {
            throw new IllegalStateException("NEURO_JOB_PERSIST_FAILED", e);
        }
    }

    private void checkpoint(Job job) {
        try { persist(job); }
        catch (RuntimeException e) { LOG.error("Neuro paid task checkpoint failed room={} job={}", job.roomId, job.id, e); }
    }

    private Job existingRoomJob(String roomId) {
        Job current = byRoom.get(roomId);
        if (current != null || jdbc == null) return current;
        List<Job> found = jdbc.query("""
            SELECT job_id,room_id,owner_uid,state,stage,provider_requests::text,
                   final_video_url,error_message
            FROM neuro_replacement_job WHERE room_id=?
            """, (rs,n) -> {
            Job restored = new Job(rs.getString(1),rs.getString(2),rs.getString(3));
            restored.state = rs.getString(4);
            restored.stage = rs.getString(5);
            try {
                JsonNode providerIds = json.readTree(rs.getString(6));
                providerIds.fields().forEachRemaining(entry -> restored.providerRequests.put(entry.getKey(), entry.getValue().asText()));
            } catch (Exception ignored) {}
            restored.finalVideo = rs.getString(7);
            restored.error = rs.getString(8);
            if (!"ready".equals(restored.state) && !"failed".equals(restored.state)) {
                restored.state = "interrupted";
                restored.stage = "После перезапуска автоматическая генерация прервана. ID GenAPI сохранены.";
            }
            return restored;
        }, roomId);
        if (found.isEmpty()) return null;
        Job raced = byRoom.putIfAbsent(roomId, found.get(0));
        return raced == null ? found.get(0) : raced;
    }


    @Value("${neuro-replacement.pilot.enabled:false}")
    private boolean enabled;

    @Value("${GENAPI_API_KEY:}")
    private String key;

    public NeuroReplacementPilotService(ObjectMapper json) {
        this.json = json;
    }

    public synchronized Map<String, Object> start(String roomId, String ownerUid, boolean consented,
                                                   MultipartFile video, MultipartFile voice) {
        return start(roomId, ownerUid, consented, video, voice, 0);
    }

    public synchronized Map<String, Object> start(String roomId, String ownerUid, boolean consented,
                                                   MultipartFile video, MultipartFile voice, double windowStartSeconds) {
        if (!Double.isFinite(windowStartSeconds) || windowStartSeconds < 0 || windowStartSeconds > 25) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_REFERENCE_WINDOW");
        }
        if (!enabled) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NEURO_REPLACEMENT_DISABLED");
        if (!consented) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EXPLICIT_IMAGE_VOICE_PAYMENT_CONSENT_REQUIRED");
        if (key == null || key.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GENAPI_API_KEY_NOT_CONFIGURED");
        Job existing = existingRoomJob(roomId);
        if (existing != null) return snapshot(existing);
        if (video == null || video.isEmpty() || video.getSize() > MAX_VIDEO_BYTES ||
                voice == null || voice.isEmpty() || voice.getSize() > MAX_VOICE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_CAMERA_OR_VOICE_SAMPLE");
        }
        byte[] videoBytes;
        byte[] voiceBytes;
        try {
            videoBytes = video.getBytes();
            voiceBytes = voice.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MEDIA_UPLOAD_FAILED");
        }
        String videoMime = video.getContentType() == null ? "" : video.getContentType().split(";")[0].trim();
        String voiceMime = voice.getContentType() == null ? "" : voice.getContentType().split(";")[0].trim();
        if (!(videoMime.equals("video/webm") || videoMime.equals("video/mp4")) ||
                !(voiceMime.equals("audio/webm") || voiceMime.equals("audio/mp4") ||
                        voiceMime.equals("audio/wav") || voiceMime.equals("audio/x-wav"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_MEDIARECORDER_FORMAT");
        }
        Job job = new Job(UUID.randomUUID().toString(), roomId, ownerUid);
        try { persist(job); }
        catch (RuntimeException e) {
            LOG.error("Refusing paid work because durable task storage failed", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "NEURO_JOB_STORAGE_UNAVAILABLE");
        }
        byRoom.put(roomId, job);
        executor.execute(() -> process(job, videoBytes, videoMime, voiceBytes, voiceMime, windowStartSeconds));
        return snapshot(job);
    }

    public Map<String, Object> status(String roomId, String ownerUid, String jobId) {
        return snapshot(find(roomId, ownerUid, jobId));
    }

    public byte[] video(String roomId, String ownerUid, String jobId) {
        Job job = find(roomId, ownerUid, jobId);
        if (!"ready".equals(job.state) || job.finalVideo == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "NEURO_REPLACEMENT_NOT_READY");
        }
        try {
            return download(job.finalVideo, MAX_RESULT_BYTES);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "NEURO_REPLACEMENT_DOWNLOAD_FAILED");
        }
    }

    private Job find(String roomId, String ownerUid, String jobId) {
        Job job = existingRoomJob(roomId);
        if (job == null || !job.id.equals(jobId) || !job.ownerUid.equals(ownerUid)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "NEURO_REPLACEMENT_JOB_NOT_FOUND");
        }
        return job;
    }

    private Map<String, Object> snapshot(Job job) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", job.id);
        result.put("status", job.state);
        result.put("stage", job.stage);
        result.put("ready", "ready".equals(job.state));
        // Expose provider IDs to the authenticated owner while still processing.
        // They can recover the paid work in GenAPI if Railway restarts.
        result.put("providerRequests", new LinkedHashMap<>(job.providerRequests));
        if (job.error != null) {
            result.put("error", job.error);
        }
        return result;
    }

    private void process(Job job, byte[] camera, String cameraMime, byte[] voice, String voiceMime, double startSeconds) {
        try {
            job.state = "processing";
            checkpoint(job);
            // Browser MediaRecorder usually gives WebM. Kling's video editor
            // requires MP4/MOV; Chatterbox is most reliable with PCM WAV.
            // Convert both *before* starting any billable GenAPI task.
            job.stage = "Подготовка записи";
            checkpoint(job);
            // Only the best ten seconds selected locally by the player device
            // leave our backend. The full observation is never sent to Kling.
            byte[] klingCamera = transcodeMedia(camera,
                    cameraMime.equals("video/mp4") ? "mp4" : "webm", "mp4", true, startSeconds);
            byte[] chatterboxVoice = transcodeMedia(voice,
                    voiceMime.contains("wav") ? "wav" : voiceMime.equals("audio/mp4") ? "m4a" : "webm",
                    "wav", false, startSeconds);
            job.stage = "Видео и озвучка";
            checkpoint(job);
            // Editing follows the input video's exact duration. Sending
            // "duration" together with edit-video is invalid for Kling O3.
            // Finish the expensive video step before ordering TTS/voice transfer:
            // do not charge for audio if Kling rejects the input.
            String videoUrl = awaitResult(job, VIDEO_MODEL_ID,
                    submitMultipart(VIDEO_MODEL_ID, klingEditFields(),
                            "video_url", "webcam.mp4", "video/mp4", klingCamera));
            job.stage = "Подготовка речи";
            checkpoint(job);
            String tts = awaitResult(job, "tts", submitJson("tts", Map.of(
                    "input", SPOKEN_TEXT, "voice", "nova", "response_format", "mp3", "speed", 1.35)));
            job.stage = "Копирование голоса";
            checkpoint(job);
            String voiceUrl = awaitResult(job, "chatterbox-speech", submitMultipart(
                    "chatterbox-speech", Map.of("source_audio_url", tts),
                    "target_voice_audio_url", "reference.wav", "audio/wav", chatterboxVoice));
            job.stage = "Синхронизация губ";
            checkpoint(job);
            String finalVideo = awaitResult(job, "lipsync", submitJson("lipsync", Map.of(
                    "video_url", videoUrl, "audio_url", voiceUrl,
                    "model", "lipsync-1.9.0-beta", "sync_mode", "cut_off")));
            validatedResultUri(finalVideo);
            job.finalVideo = finalVideo;
            job.stage = "Готово";
            job.state = "ready";
            checkpoint(job);
            LOG.info("Neuro finished job={} room={} providerRequests={}", job.id, job.roomId, job.providerRequests);
        } catch (Exception error) {
            job.state = "failed";
            job.stage = "Ошибка генерации";
            // CompletionException -> RuntimeException -> provider IOException.
            // Unwrap all layers so the model, request ID and original reason
            // survive all the way to the owner's test UI.
            Throwable root = error;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            String message = String.valueOf(root.getMessage());
            job.error = message.length() > 350 ? message.substring(0, 350) : message;
            checkpoint(job);
            LOG.warn("Neuro replacement failed: room={} job={} providerRequests={} reason={}",
                    job.roomId, job.id, job.providerRequests, job.error);
        }
    }

    /**
     * Normalizes browser media without contacting GenAPI or consuming credits.
     * FFmpeg is installed in the Railway runtime image. Uploads are capped
     * before and after transcoding so a malformed video cannot exhaust RAM.
     */
    /** GenAPI Kling O3 video editing uses the uploaded clip length, not a duration field. */
    private Map<String, String> klingEditFields() {
        return Map.of(
                "model", "edit-video",
                "prompt", VIDEO_PROMPT,
                "pro", "true",
                "keep_audio", "false"
        );
    }

    private byte[] transcodeMedia(byte[] source, String inputExtension,
                                  String outputExtension, boolean video, double startSeconds) throws Exception {
        Path input = Files.createTempFile("hat-neuro-input-", "." + inputExtension);
        Path output = Files.createTempFile("hat-neuro-output-", "." + outputExtension);
        try {
            Files.write(input, source);
            List<String> command = new ArrayList<>(List.of(
                    "ffmpeg", "-hide_banner", "-loglevel", "error",
                    "-nostdin", "-y", "-ss", String.valueOf(startSeconds), "-i", input.toString(),
                    "-t", "10"));
            if (video) {
                // Kling video editing requires >=720px on BOTH dimensions,
                // H.264 MP4, and 24-60fps. A fallback LiveKit stream may be
                // only 640x360 / 15fps. Normalize it BEFORE the paid request.
                command.addAll(List.of("-map", "0:v:0", "-an",
                        "-vf", "scale=1280:720:force_original_aspect_ratio=decrease:flags=lanczos,"
                                + "pad=1280:720:(ow-iw)/2:(oh-ih)/2,fps=24,setsar=1",
                        "-c:v", "libx264", "-preset", "veryfast", "-crf", "19",
                        "-pix_fmt", "yuv420p", "-movflags", "+faststart"));
            } else {
                command.addAll(List.of("-vn", "-ac", "1", "-ar", "24000",
                        "-c:a", "pcm_s16le"));
            }
            command.add(output.toString());
            Process tool = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!tool.waitFor(90, TimeUnit.SECONDS)) {
                tool.destroyForcibly();
                throw new IOException("FFMPEG_PREPARATION_TIMEOUT");
            }
            if (tool.exitValue() != 0) {
                String diagnostic = new String(tool.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                        .replaceAll("[^\\p{L}\\p{N} _.,:;!()#-]", " ").trim();
                throw new IOException("FFMPEG_PREPARATION_FAILED " +
                        diagnostic.substring(0, Math.min(140, diagnostic.length())));
            }
            long bytes = Files.size(output);
            int max = video ? MAX_VIDEO_BYTES : MAX_VOICE_BYTES;
            if (bytes == 0 || bytes > max) throw new IOException("PREPARED_MEDIA_SIZE_INVALID");
            // Reject too-short or invalid excerpts locally before a paid API POST.
            Process probe = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries",
                    "format=duration", "-of", "default=noprint_wrappers=1:nokey=1",
                    output.toString()).start();
            if (!probe.waitFor(12, TimeUnit.SECONDS) || probe.exitValue() != 0) {
                probe.destroyForcibly();
                throw new IOException("PREPARED_MEDIA_PROBE_FAILED");
            }
            double seconds;
            try {
                seconds = Double.parseDouble(new String(probe.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8).trim());
            } catch (NumberFormatException exception) {
                throw new IOException("PREPARED_MEDIA_DURATION_UNKNOWN", exception);
            }
            if (!Double.isFinite(seconds) || seconds < 9.2) {
                throw new IOException("PREPARED_MEDIA_TOO_SHORT " + seconds + "s");
            }
            return Files.readAllBytes(output);
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
        }
    }

    private String submitJson(String model, Map<String, ?> fields) throws Exception {
        String body = json.writeValueAsString(fields);
        HttpRequest request = baseRequest(GENAPI.resolve("networks/" + model))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        try {
            return requestId(send(request));
        } catch (Exception error) {
            throw new IOException("GenAPI " + model + " submission: " + error.getMessage(), error);
        }
    }

    private String submitMultipart(String model, Map<String, String> fields, String fileField,
                                   String name, String mime, byte[] data) throws Exception {
        String boundary = "hot-hat-" + UUID.randomUUID();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            write(bytes, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey() +
                    "\"\r\n\r\n" + field.getValue() + "\r\n");
        }
        write(bytes, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + fileField +
                "\"; filename=\"" + name + "\"\r\nContent-Type: " + mime + "\r\n\r\n");
        bytes.write(data);
        write(bytes, "\r\n--" + boundary + "--\r\n");
        HttpRequest request = baseRequest(GENAPI.resolve("networks/" + model))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build();
        try {
            return requestId(send(request));
        } catch (Exception error) {
            throw new IOException("GenAPI " + model + " submission: " + error.getMessage(), error);
        }
    }

    private void write(ByteArrayOutputStream bytes, String str) throws IOException {
        bytes.write(str.getBytes(StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder baseRequest(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(100))
                .header("Authorization", "Bearer " + key).header("Accept", "application/json");
    }

    private JsonNode send(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode data = json.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GENAPI_HTTP_" + response.statusCode() + " " + errorCode(data));
        }
        return data;
    }

    private String requestId(JsonNode data) throws IOException {
        String id = data.path("request_id").asText("");
        if (id.isBlank()) id = data.path("id").asText("");
        if (!id.matches("[a-zA-Z0-9_-]{1,100}")) throw new IOException("GENAPI_REQUEST_ID_MISSING");
        return id;
    }

    private String awaitResult(Job job, String model, String id) throws Exception {
        job.providerRequests.put(model, id);
        checkpoint(job);
        LOG.info("Neuro provider started job={} model={} request_id={}", job.id, model, id);
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(30);
        while (System.nanoTime() < deadline) {
            JsonNode data = send(baseRequest(GENAPI.resolve("request/get/" + id)).GET().build());
            String status = data.path("status").asText("").toLowerCase();
            if ("success".equals(status) || "completed".equals(status)) {
                JsonNode result = data.path("result");
                String url = result.isArray() && !result.isEmpty() ? result.get(0).asText("") : result.asText("");
                validatedResultUri(url);
                return url;
            }
            if ("error".equals(status) || "failed".equals(status) || "failure".equals(status) || "canceled".equals(status)) {
                String details = errorCode(data);
                throw new IOException("GenAPI " + model + " status=" + status +
                        ", request_id=" + id + ": " +
                        (details.isBlank() ? "сервис не передал причину ошибки" : details));
            }
            Thread.sleep(6500);
        }
        throw new IOException("GenAPI " + model + " timeout (30 мин.), request_id=" + id);
    }

    private String errorCode(JsonNode body) {
        String message = findProviderError(body, 0);
        String safe = message.replaceAll("[^\\p{L}\\p{N} _.,:;!()#-]", " ").trim();
        return safe.substring(0, Math.min(safe.length(), 180));
    }

    private String findProviderError(JsonNode node, int depth) {
        if (node == null || node.isNull() || depth > 4) return "";
        if (node.isObject()) {
            for (String name : new String[]{"error", "error_message", "message", "detail", "reason", "description"}) {
                JsonNode value = node.path(name);
                if (value.isTextual() && !value.asText("").isBlank()) return value.asText();
                if (value.isContainerNode()) {
                    String nested = findProviderError(value, depth + 1);
                    if (!nested.isBlank()) return nested;
                }
            }
            for (JsonNode value : node) {
                if (value.isContainerNode()) {
                    String nested = findProviderError(value, depth + 1);
                    if (!nested.isBlank()) return nested;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode value : node) {
                String nested = findProviderError(value, depth + 1);
                if (!nested.isBlank()) return nested;
            }
        }
        return "";
    }

    private URI validatedResultUri(String url) throws IOException {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            boolean trusted = host.endsWith(".gen-api.ru") || host.equals("gen-api.storage.yandexcloud.net");
            if (!"https".equals(uri.getScheme()) || !trusted ||
                    uri.getRawUserInfo() != null || uri.getPort() != -1) throw new IllegalArgumentException();
            return uri;
        } catch (RuntimeException exception) {
            throw new IOException("UNTRUSTED_GENAPI_RESULT_URL");
        }
    }

    private byte[] download(String url, int maxBytes) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(validatedResultUri(url))
                .timeout(Duration.ofSeconds(90)).GET().build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200 || response.body().length > maxBytes) {
            throw new IOException("GENAPI_RESULT_DOWNLOAD_FAILED");
        }
        return response.body();
    }

    @PreDestroy
    public void close() { executor.shutdownNow(); }

    private static final class Job {
        final String id;
        final String roomId;
        final String ownerUid;
        volatile String state = "queued";
        volatile String stage = "В очереди";
        volatile String finalVideo;
        volatile String error;
        final Map<String, String> providerRequests = new ConcurrentHashMap<>();

        Job(String id, String roomId, String ownerUid) {
            this.id = id;
            this.roomId = roomId;
            this.ownerUid = ownerUid;
        }
    }
}
