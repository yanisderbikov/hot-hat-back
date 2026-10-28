package ru.hothat.testbot.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.concurrent.CompletableFuture;
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
    private static final String VIDEO_PROMPT = "Edit the attached video. Keep precisely the SAME real webcam person, facial identity, outfit, room, "+
            "camera angle, lighting, desk and background as the original footage. One static webcam shot, no cuts, "+
            "no scene changes, no other people, no captions. The person naturally faces the camera and talks for "+
            "roughly four seconds, then raises one hand and gives a clearly visible thumbs-up directly toward the "+
            "camera (fingers correctly formed), holds it for a moment, lowers the hand, then talks again to the "+
            "camera until the end. Natural unscripted webcam movement; keep lips visible. Exactly ten seconds.";

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

    @Value("${neuro-replacement.pilot.enabled:false}")
    private boolean enabled;

    @Value("${GENAPI_API_KEY:}")
    private String key;

    public NeuroReplacementPilotService(ObjectMapper json) {
        this.json = json;
    }

    public synchronized Map<String, Object> start(String roomId, String ownerUid, boolean consented,
                                                   MultipartFile video, MultipartFile voice) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NEURO_REPLACEMENT_DISABLED");
        if (!consented) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EXPLICIT_IMAGE_VOICE_PAYMENT_CONSENT_REQUIRED");
        if (key == null || key.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GENAPI_API_KEY_NOT_CONFIGURED");
        Job existing = byRoom.get(roomId);
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
        byRoom.put(roomId, job);
        executor.execute(() -> process(job, videoBytes, videoMime, voiceBytes, voiceMime));
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
        Job job = byRoom.get(roomId);
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
        if (job.error != null) {
            result.put("error", job.error);
            result.put("providerRequests", new LinkedHashMap<>(job.providerRequests));
        }
        return result;
    }

    private void process(Job job, byte[] camera, String cameraMime, byte[] voice, String voiceMime) {
        try {
            job.state = "processing";
            // Browser MediaRecorder usually gives WebM. Kling's video editor
            // requires MP4/MOV; Chatterbox is most reliable with PCM WAV.
            // Convert both *before* starting any billable GenAPI task.
            job.stage = "Подготовка записи";
            byte[] klingCamera = cameraMime.equals("video/webm")
                    ? transcodeMedia(camera, "webm", "mp4", true) : camera;
            byte[] chatterboxVoice = voiceMime.contains("wav")
                    ? voice : transcodeMedia(voice, voiceMime.equals("audio/mp4") ? "m4a" : "webm", "wav", false);
            job.stage = "Видео и озвучка";
            // Both models start at once. Only one operation per model: retrying a
            // submission after an HTTP timeout could charge the user twice.
            CompletableFuture<String> videoFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return awaitResult(job, "kling-video-o1", submitMultipart("kling-video-o1", Map.of(
                            "model", "edit-video", "prompt", VIDEO_PROMPT, "duration", "10",
                            "pro", "true", "keep_audio", "false"),
                            "video_url", "webcam.mp4", "video/mp4", klingCamera));
                } catch (Exception exception) { throw new RuntimeException(exception); }
            }, executor);
            CompletableFuture<String> audioFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    String tts = awaitResult(job, "tts", submitJson("tts", Map.of(
                            "input", SPOKEN_TEXT, "voice", "nova", "response_format", "mp3", "speed", 1.35)));
                    job.stage = "Копирование голоса";
                    return awaitResult(job, "chatterbox-speech", submitMultipart("chatterbox-speech", Map.of(
                            "source_audio_url", tts), "target_voice_audio_url",
                            "reference.wav", "audio/wav", chatterboxVoice));
                } catch (Exception exception) { throw new RuntimeException(exception); }
            }, executor);
            String videoUrl = videoFuture.join();
            String voiceUrl = audioFuture.join();
            job.stage = "Синхронизация губ";
            String finalVideo = awaitResult(job, "lipsync", submitJson("lipsync", Map.of(
                    "video_url", videoUrl, "audio_url", voiceUrl,
                    "model", "lipsync-1.9.0-beta", "sync_mode", "cut_off")));
            validatedResultUri(finalVideo);
            job.finalVideo = finalVideo;
            job.stage = "Готово";
            job.state = "ready";
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
            LOG.warn("Neuro replacement failed: room={} job={} providerRequests={} reason={}",
                    job.roomId, job.id, job.providerRequests, job.error);
        }
    }

    /**
     * Normalizes browser media without contacting GenAPI or consuming credits.
     * FFmpeg is installed in the Railway runtime image. Uploads are capped
     * before and after transcoding so a malformed video cannot exhaust RAM.
     */
    private byte[] transcodeMedia(byte[] source, String inputExtension,
                                  String outputExtension, boolean video) throws Exception {
        Path input = Files.createTempFile("hat-neuro-input-", "." + inputExtension);
        Path output = Files.createTempFile("hat-neuro-output-", "." + outputExtension);
        try {
            Files.write(input, source);
            List<String> command = new ArrayList<>(List.of(
                    "ffmpeg", "-hide_banner", "-loglevel", "error",
                    "-nostdin", "-y", "-i", input.toString()));
            if (video) {
                command.addAll(List.of("-map", "0:v:0", "-an", "-c:v", "libx264",
                        "-preset", "veryfast", "-crf", "25", "-pix_fmt", "yuv420p",
                        "-movflags", "+faststart"));
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
