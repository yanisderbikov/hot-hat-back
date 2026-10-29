package ru.hothat.testbot.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import java.util.concurrent.Callable;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.hothat.common.storage.ObjectStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
    // This is a fresh performance, not an edit of the recorded person's gestures.
    // The reference camera video supplies identity, webcam room, and habitual
    // facial presence; the directed acting comes from the prompt.
    private static final String SPOKEN_TEXT = "А вы всё ещё кипятите? ... Тогда мы идём к вам!";
    private static final String VIDEO_MODEL_ID = "kling-video-o3";
    private static final String VIDEO_ACTING_MODE = "reference-to-video";
    private static final int VIDEO_DURATION_SECONDS = 15;
    private static final String LIPSYNC_SYNC_MODE = "silence";
    private static final String TTS_MODEL_ID = "gemini-tts";
    private static final String SPEECH_STYLE =
            "Russian male human speaker delivering a playful, expressive, clear catchphrase to a friend. " +
            "Warm natural conversational intonation, precise Russian pronunciation and consonants. " +
            "Speak unhurriedly; ask the first question with amused curiosity, take a meaningful pause, " +
            "then say the second sentence with a confident smile and enthusiastic emphasis. " +
            "No announcer voice, no robotic monotone, no shouting, no added words. " +
            "The question and exclamation must remain clearly intelligible.";
    private static final String VIDEO_PROMPT =
            "Create a NEW realistic, continuous 15-second webcam performance based on @Video1 as an " +
            "APPEARANCE AND SET REFERENCE ONLY, NOT as motion to copy. Preserve exactly the same adult " +
            "man's recognizable facial proportions, beard, hairstyle, age, white T-shirt, lighting, " +
            "window, furniture, room, fixed webcam position and framing. Observe the person's natural " +
            "facial presence and microexpressions in the reference; make the new performance convincingly " +
            "human, with relaxed breathing, genuine playful smiling, natural head motion, no stiff gestures. " +
            "IMPORTANT: entirely REPLACE the recorded head-scratching/hair-touching motion. " +
            "The man MUST NOT touch his hair, face, head or ears. " +
            "0-1.5s: sitting naturally and looking slightly away, he turns his head to look directly " +
            "into the webcam and makes warm eye contact. " +
            "1.5-4.8s: he looks straight at us with an amused, expressive questioning face and clearly " +
            "speaks the Russian phrase 'А вы всё ещё кипятите?' Slowly, with animated eyebrows and " +
            "distinct speech mouth movement. " +
            "4.8-5.5s: deliberate short silent pause; mouth closed, engaging eye contact. " +
            "5.5-8.5s: he confidently and playfully speaks 'Тогда мы идём к вам!' with a broad genuine " +
            "smile, looking straight into the lens. " +
            "8.5-10s: he raises his RIGHT hand clearly in front of his chest, close to the lens and " +
            "fully visible, gives an unmistakable THUMBS-UP (only thumb extended vertically, other " +
            "four fingers curled), holds it long enough to recognize, and smiles. " +
            "10-11s: he lowers that hand naturally. " +
            "11-12.5s: raises his open right hand again, gives a clear friendly SIDE-TO-SIDE GOODBYE WAVE " +
            "toward the camera. Five fingers spread, palm facing camera, hand moves left and right. " +
            "12.5-15s: lowers his hand, pushes back from his chair, clearly STANDS UP and WALKS AWAY " +
            "toward the side of the room, fully leaving the webcam view. The chair remains empty. " +
            "One seamless static shot, real hands and fingers, photorealistic identity, unbroken room. " +
            "Mouth should move ONLY while the two sentences are spoken; mouth closed in the rest. " +
            "NO hair grooming, NO frozen head pose, NO camera movement, NO jump cut, " +
            "NO duplicate person, NO subtitles, NO extra objects, NO alternate dialogue.";
    private final ObjectMapper json;
    // Package-private HTTP injection seam enables a complete no-charge provider
    // simulation in JUnit. Production still uses the real pinned endpoint.
    private final HttpClient http;
    private final ExecutorService executor = Executors.newCachedThreadPool(task -> {
        Thread worker = new Thread(task, "neuro-replacement-pilot");
        worker.setDaemon(true);
        return worker;
    });
    // A paid MP4 recovery can decode/encode HD video. Never perform that work
    // in an HTTP GET handler or run concurrent heavyweight repair processes.
    private final ExecutorService recoveryExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread worker = new Thread(task, "neuro-paid-mp4-recovery");
        worker.setDaemon(true);
        return worker;
    });
    private final Map<String, Job> byRoom = new ConcurrentHashMap<>();
    private JdbcTemplate jdbc;
    private ObjectStore objectStore;

    @Autowired(required = false)
    public void setObjectStore(ObjectStore objectStore) { this.objectStore = objectStore; }

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
                 (job_id,room_id,owner_uid,state,stage,provider_requests,final_video_url,final_video_key,error_message,
                  source_camera_key,source_voice_key,source_camera_mime,source_voice_mime,window_start_seconds,submitting_model,updated_at)
                VALUES (?,?,?,?,?,CAST(? AS jsonb),?,?,?,?,?,?,?,?,?,now())
                ON CONFLICT (job_id) DO UPDATE SET
                 state=EXCLUDED.state,stage=EXCLUDED.stage,
                 provider_requests=EXCLUDED.provider_requests,
                 final_video_url=EXCLUDED.final_video_url,
                 final_video_key=EXCLUDED.final_video_key,
                 error_message=EXCLUDED.error_message,
                 source_camera_key=EXCLUDED.source_camera_key,
                 source_voice_key=EXCLUDED.source_voice_key,
                 source_camera_mime=EXCLUDED.source_camera_mime,
                 source_voice_mime=EXCLUDED.source_voice_mime,
                 window_start_seconds=EXCLUDED.window_start_seconds,
                 submitting_model=EXCLUDED.submitting_model,updated_at=now()
                """,job.id,job.roomId,job.ownerUid,job.state,job.stage,
                json.writeValueAsString(new LinkedHashMap<>(job.providerRequests)),
                job.finalVideo,job.finalVideoKey,job.error,
                job.sourceCameraKey,job.sourceVoiceKey,job.cameraMime,job.voiceMime,
                job.startSeconds,job.submittingModel);
        } catch (Exception e) {
            throw new IllegalStateException("NEURO_JOB_PERSIST_FAILED", e);
        }
    }

    private void checkpoint(Job job) {
        try { persist(job); }
        catch (RuntimeException e) { LOG.error("Neuro paid task checkpoint failed room={} job={}", job.roomId, job.id, e); }
    }

    private Job loadJobById(String id) {
        if (jdbc == null) return null;
        List<Job> jobs = jdbc.query("""
            SELECT job_id,room_id,owner_uid,state,stage,provider_requests::text,
                   final_video_url,error_message,final_video_key,
                   source_camera_key,source_voice_key,source_camera_mime,source_voice_mime,
                   window_start_seconds,submitting_model
              FROM neuro_replacement_job WHERE job_id=?
            """, (rs,n) -> {
            Job job = new Job(rs.getString(1), rs.getString(2), rs.getString(3));
            job.state=rs.getString(4); job.stage=rs.getString(5);
            try {
                JsonNode ids=json.readTree(rs.getString(6));
                ids.fields().forEachRemaining(e -> job.providerRequests.put(e.getKey(),e.getValue().asText()));
            } catch (Exception ignored) {}
            job.finalVideo=rs.getString(7); job.error=rs.getString(8); job.finalVideoKey=rs.getString(9);
            job.sourceCameraKey=rs.getString(10);job.sourceVoiceKey=rs.getString(11);
            job.cameraMime=rs.getString(12);job.voiceMime=rs.getString(13);
            job.startSeconds=rs.getDouble(14);job.submittingModel=rs.getString(15);
            return job;
        }, id);
        return jobs.isEmpty()?null:jobs.get(0);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingJobs() {
        if (jdbc == null || objectStore == null || !objectStore.configured()) return;
        try {
            List<String> pending = jdbc.query("""
                SELECT job_id FROM neuro_replacement_job WHERE state IN ('queued','processing')
                ORDER BY created_at ASC LIMIT 25
                """, (rs,n) -> rs.getString(1));
            for (String id : pending) {
                Job job = loadJobById(id);
                if (job == null) continue;
                if (job.sourceCameraKey == null || job.sourceVoiceKey == null) {
                    job.state="interrupted";
                    job.stage="Прежняя версия не сохранила исходные файлы для восстановления";
                    job.error="Нет архива входных материалов. Требуется ручная проверка ID GenAPI.";
                    checkpoint(job);
                    continue;
                }
                if (job.submittingModel != null
                        && !job.providerRequests.containsKey(job.submittingModel)) {
                    // The remote POST might have succeeded before the worker
                    // received its ID. NEVER charge twice due to a redeploy.
                    job.state="interrupted";
                    job.stage="Неоднозначный платный запрос "+job.submittingModel;
                    job.error="Нельзя автоматически повторять POST без сверки с GenAPI.";
                    checkpoint(job);
                    continue;
                }
                // Replace any pre-startup cached snapshot with the authoritative
                // job that the recovery worker will update. Otherwise a GET
                // racing ApplicationReadyEvent can cache an orphaned object.
                byRoom.put(job.roomId, job);
                executor.execute(() -> {
                    try {
                        process(job, objectStore.get(job.sourceCameraKey), job.cameraMime,
                                objectStore.get(job.sourceVoiceKey),job.voiceMime,job.startSeconds);
                    } catch (Exception e) {
                        job.state="failed";
                        job.stage="Не удалось восстановить исходные файлы";
                        job.error="RECOVERY_INPUT_FAILED: "+e.getClass().getSimpleName();
                        checkpoint(job);
                    }
                });
            }
        } catch (Exception e) {
            LOG.error("Neuro pending-job recovery failed", e);
        }
    }

    /** Read a historical task for this owner, independent of room lifecycle. */
    private Job findOwnerJob(String ownerUid, String jobId) {
        if (jdbc == null) return null;
        List<Job> rows = jdbc.query("""
            SELECT job_id,room_id,owner_uid,state,stage,provider_requests::text,
                   final_video_url,error_message,final_video_key
            FROM neuro_replacement_job WHERE job_id=? AND owner_uid=?
            """, (rs, rowNum) -> {
            Job job = new Job(rs.getString(1), rs.getString(2), rs.getString(3));
            job.state = rs.getString(4);
            job.stage = rs.getString(5);
            try {
                JsonNode ids = json.readTree(rs.getString(6));
                ids.fields().forEachRemaining(e -> job.providerRequests.put(e.getKey(),e.getValue().asText()));
            } catch (Exception ignored) {}
            job.finalVideo = rs.getString(7);
            job.error = rs.getString(8);
            job.finalVideoKey = rs.getString(9);
            return job;
        }, jobId, ownerUid);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** All paid attempts, latest first, including failures and interrupted tasks. */
    /** Free readiness check before the user authorizes an expensive AI task. */
    public Map<String,Boolean> readiness() {
        return Map.of("archiveReady",objectStore != null && objectStore.configured(),
                "databaseReady",jdbc != null);
    }

    public List<Map<String,Object>> history(String ownerUid) {
        if (jdbc == null) return List.of();
        return jdbc.query("""
            SELECT job_id,room_id,owner_uid,state,stage,provider_requests::text,
                   final_video_url,error_message,final_video_key,created_at
            FROM neuro_replacement_job WHERE owner_uid=? ORDER BY created_at DESC LIMIT 200
            """, (rs, rowNum) -> {
            Job job = new Job(rs.getString(1),rs.getString(2),rs.getString(3));
            job.state = rs.getString(4); job.stage = rs.getString(5);
            try {
                JsonNode ids = json.readTree(rs.getString(6));
                ids.fields().forEachRemaining(e -> job.providerRequests.put(e.getKey(),e.getValue().asText()));
            } catch (Exception ignored) {}
            job.finalVideo = rs.getString(7);
            job.error = rs.getString(8); job.finalVideoKey = rs.getString(9);
            Map<String,Object> result = snapshot(job);
            result.put("roomId",job.roomId);
            result.put("createdAt",rs.getTimestamp(10).toInstant().toString());
            result.put("archived",job.finalVideoKey != null);
            // PostgreSQL is authoritative. A cached worker snapshot can be
            // stale after another instance advances a paid task (or restores it).
            // Never hide a real failure/ready state behind cached "processing".
            return result;
        }, ownerUid);
    }

    /**
     * Read-only provider investigation. Only GET /request/get/{id}, NEVER
     * networks/{model} POST, so checking an old paid attempt is free.
     * If the final lip-sync result already exists, save its MP4 now.
     */
    public Map<String,Object> inspectProvider(String ownerUid, String jobId) {
        // Load the full checkpoint: findOwnerJob() omits S3 input references,
        // and persisting that partial row could erase previously paid inputs.
        Job job = loadJobById(jobId);
        if (job == null || !ownerUid.equals(job.ownerUid)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "NEURO_JOB_NOT_FOUND");
        }
        Map<String,Object> results = new LinkedHashMap<>();
        for (Map.Entry<String,String> entry : new LinkedHashMap<>(job.providerRequests).entrySet()) {
            try {
                String id = entry.getValue();
                JsonNode data = send(baseRequest(GENAPI.resolve("request/get/"+id)).GET().build());
                Map<String,Object> item = new LinkedHashMap<>();
                item.put("requestId", id);
                item.put("status", data.path("status").asText("unknown"));
                item.put("progress", data.path("progress").asInt(0));
                item.put("cost", data.path("cost").isNumber()?data.path("cost").asDouble():null);
                item.put("error",errorCode(data));
                results.put(entry.getKey(),item);
                // This endpoint must stay a fast, side-effect-free GET.
                // Large MP4 downloads/FFmpeg belong to the explicit async
                // no-charge /recover action, not to health/status polling.
            } catch (Exception error) {
                Map<String,Object> item = new LinkedHashMap<>();
                Object previous = results.get(entry.getKey());
                if (previous instanceof Map<?,?> previousInfo) {
                    previousInfo.forEach((k,v) -> item.put(String.valueOf(k),v));
                }
                item.put("requestId",entry.getValue());
                item.put("recoveryError",String.valueOf(error.getMessage()));
                results.put(entry.getKey(),item);
            }
        }
        Map<String,Object> response=new LinkedHashMap<>();
        response.put("jobId",jobId);
        response.put("status",job.state);
        response.put("stage",job.stage);
        if (job.error != null) response.put("error",job.error);
        response.put("providerRequests",results);
        if (results.isEmpty()) {
            response.put("note","Сервер не получил ни одного ID GenAPI: платная отправка не подтверждена.");
        } else if (job.providerRequests.containsKey("lipsync") &&
                !"ready".equals(job.state)) {
            response.put("note", "Lip Sync уже оплачен. Проверка GenAPI не скачивает большие файлы и не запускает FFmpeg. Для восстановления MP4 используйте отдельную кнопку «Восстановить MP4 без оплаты».");
        } else if (!job.providerRequests.containsKey("lipsync")) {
            boolean prerequisitesSucceeded = true;
            for (String model : new String[]{VIDEO_MODEL_ID, "tts", "chatterbox-speech"}) {
                Object info = results.get(model);
                if (!(info instanceof Map<?,?> stage) ||
                        !"success".equalsIgnoreCase(String.valueOf(stage.get("status")))) {
                    prerequisitesSucceeded = false;
                    break;
                }
            }
            response.put("note", prerequisitesSucceeded
                    ? "Kling, TTS и Chatterbox завершены. Но ID Lip Sync отсутствует: итогового MP4 пока нет. Эта проверка не запускала новую платную генерацию."
                    : "ID Lip Sync пока отсутствует. Итоговый MP4 не подтверждён; проверка выполнена без новых платных запросов.");
        }
        return response;
    }


    /**
     * Restore an ALREADY PAID lip-synced result. No model POST is issued:
     * only previously saved request IDs are read with GET. Heavy media
     * processing happens on a single background worker, not the HTTP thread.
     * A PostgreSQL conditional claim makes browser double-clicks and separate
     * Railway instances safe; incomplete or unverifiable outputs stay failed.
     */
    public synchronized Map<String,Object> recoverPaidOutput(String ownerUid, String jobId) {
        if (jdbc == null || objectStore == null || !objectStore.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "NEURO_RESULT_ARCHIVE_NOT_CONFIGURED");
        }
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GENAPI_API_KEY_NOT_CONFIGURED");
        }
        Job job = loadJobById(jobId);
        if (job == null || !ownerUid.equals(job.ownerUid)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "NEURO_JOB_NOT_FOUND");
        }
        if (!("failed".equals(job.state) || "interrupted".equals(job.state)) ||
                job.finalVideoKey != null ||
                !job.providerRequests.containsKey(VIDEO_MODEL_ID) ||
                !job.providerRequests.containsKey("tts") ||
                !job.providerRequests.containsKey("chatterbox-speech") ||
                !job.providerRequests.containsKey("lipsync")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "NEURO_PAID_RESULT_RECOVERY_NOT_ELIGIBLE");
        }
        int claimed = jdbc.update("""
                UPDATE neuro_replacement_job
                   SET state='processing', stage='Восстановление уже оплаченного MP4',
                       error_message=NULL
                 WHERE job_id=? AND owner_uid=?
                   AND state IN ('failed','interrupted')
                   AND final_video_key IS NULL
                   AND jsonb_exists(provider_requests, 'kling-video-o3')
                   AND jsonb_exists(provider_requests, 'tts')
                   AND jsonb_exists(provider_requests, 'chatterbox-speech')
                   AND jsonb_exists(provider_requests, 'lipsync')
                """, jobId, ownerUid);
        if (claimed != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "NEURO_PAID_RESULT_RECOVERY_ALREADY_CLAIMED");
        }
        job.state="processing";
        job.stage="Восстановление уже оплаченного MP4";
        job.error=null;
        byRoom.put(job.roomId, job);
        recoveryExecutor.execute(() -> {
            try {
                String url = completedPaidResultUrl("lipsync", job.providerRequests.get("lipsync"));
                byte[] mp4 = recoverPaidLipSyncVideo(job, url);
                String archiveKey="neuro-replacements/"+ownerUid+"/"+job.id+".mp4";
                objectStore.put(archiveKey,mp4,"video/mp4");
                job.finalVideo=url;
                job.finalVideoKey=archiveKey;
                job.stage="Готово (восстановлено без GenAPI POST)";
                job.state="ready";
                job.error=null;
                checkpoint(job);
                LOG.info("Recovered paid neuro result job={} from existing lip-sync ID without POST",job.id);
            } catch (Exception error) {
                // Railway deployment interrupts workers. Leave the durable
                // processing checkpoint for free recovery after restart.
                if (error instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                    LOG.info("Paid MP4 recovery paused by deployment job={}",job.id);
                    return;
                }
                job.state="failed";
                job.stage="Ошибка бесплатного восстановления оплаченного MP4";
                String details = String.valueOf(error.getMessage());
                job.error = details.substring(0, Math.min(350, details.length()));
                checkpoint(job);
                LOG.warn("Paid result recovery failed job={} lipSyncId={} reason={}",
                        job.id,job.providerRequests.get("lipsync"),job.error);
            }
        });
        return snapshot(job);
    }

    /**
     * Explicitly approved continuation after a definite GenAPI HTTP 402.
     * The already-paid Kling/TTS/Chatterbox request IDs remain untouched;
     * process() uses only GET for them, then submits ONE new lip-sync POST.
     * The conditional SQL claim prevents two API calls on double-click or
     * concurrent owner sessions / application instances. No automatic retry
     * occurs for an ambiguous POST, a provider timeout or a generic failure.
     */
    public synchronized Map<String,Object> resumeAfterLimit(String ownerUid, String jobId,
                                                              boolean consented) {
        if (!consented) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "EXPLICIT_LIPSYNC_PAYMENT_CONSENT_REQUIRED");
        }
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NEURO_REPLACEMENT_DISABLED");
        }
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GENAPI_API_KEY_NOT_CONFIGURED");
        }
        if (jdbc == null || objectStore == null || !objectStore.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "NEURO_RESULT_ARCHIVE_NOT_CONFIGURED");
        }
        Job job = loadJobById(jobId);
        if (job == null || !job.ownerUid.equals(ownerUid)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "NEURO_JOB_NOT_FOUND");
        }
        // 402 proves that the provider refused the lip-sync POST; an ambiguous
        // timeout is NEVER sufficient evidence for a safe retry.
        if (!"failed".equals(job.state) || job.error == null ||
                !job.error.startsWith("GENAPI_HTTP_402") ||
                job.providerRequests.containsKey("lipsync") ||
                !(job.submittingModel == null || "lipsync".equals(job.submittingModel)) ||
                !job.providerRequests.containsKey(VIDEO_MODEL_ID) ||
                !job.providerRequests.containsKey("tts") ||
                !job.providerRequests.containsKey("chatterbox-speech") ||
                job.sourceCameraKey == null || job.sourceVoiceKey == null ||
                job.cameraMime == null || job.voiceMime == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "NEURO_NOT_ELIGIBLE_FOR_402_LIPSYNC_RESUME");
        }
        // Atomic PostgreSQL check: the old 402 error must still be there and
        // no lip-sync ID may have appeared since the owner opened the page.
        int claimed = jdbc.update("""
                UPDATE neuro_replacement_job
                   SET state='processing', stage='Продолжение после лимита: проверка оплаченных этапов',
                       error_message=NULL, submitting_model=NULL
                 WHERE job_id=? AND owner_uid=? AND state='failed'
                   AND error_message LIKE 'GENAPI_HTTP_402%'
                   AND NOT jsonb_exists(provider_requests, 'lipsync')
                """, jobId, ownerUid);
        if (claimed != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "NEURO_LIPSYNC_RESUME_ALREADY_CLAIMED");
        }
        job.state="processing";
        job.stage="Продолжение после лимита: проверка оплаченных этапов";
        job.error=null;
        job.submittingModel=null; // Clear only the definitively refused 402 POST.
        byRoom.put(job.roomId, job);
        executor.execute(() -> {
            try {
                // Read original archived media; never request a new webcam
                // sample or redo any of the three successful paid stages.
                byte[] camera = objectStore.get(job.sourceCameraKey);
                byte[] voice = objectStore.get(job.sourceVoiceKey);
                process(job,camera,job.cameraMime,voice,job.voiceMime,job.startSeconds);
            } catch (Exception e) {
                job.state="failed";
                job.stage="Не удалось продолжить после лимита";
                job.error="NEURO_RESUME_INPUT_FAILED: " + e.getClass().getSimpleName();
                checkpoint(job);
            }
        });
        return snapshot(job);
    }

    public byte[] archivedVideo(String ownerUid, String jobId) {
        Job job = findOwnerJob(ownerUid,jobId);
        if (job == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"NEURO_JOB_NOT_FOUND");
        if (!"ready".equals(job.state))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"NEURO_REPLACEMENT_NOT_READY");
        try {
            if (job.finalVideoKey != null && objectStore != null && objectStore.configured())
                return objectStore.get(job.finalVideoKey);
            if (job.finalVideo != null) return download(job.finalVideo, MAX_RESULT_BYTES);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"NEURO_VIDEO_DOWNLOAD_FAILED",e);
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,"NEURO_VIDEO_UNAVAILABLE");
    }

    private Job existingRoomJob(String roomId) {
        Job current = byRoom.get(roomId);
        if (current != null || jdbc == null) return current;
        List<String> ids = jdbc.query("""
            SELECT job_id FROM neuro_replacement_job
            WHERE room_id=? ORDER BY created_at DESC LIMIT 1
            """, (rs,n) -> rs.getString(1), roomId);
        if (ids.isEmpty()) return null;
        // Load the full checkpoint, including S3 keys and the in-flight paid
        // submission marker. A partial row must never enter the cache.
        Job restored = loadJobById(ids.get(0));
        if (restored == null) return null;
        Job raced = byRoom.putIfAbsent(roomId, restored);
        return raced == null ? restored : raced;
    }


    @Value("${neuro-replacement.pilot.enabled:false}")
    private boolean enabled;

    @Value("${GENAPI_API_KEY:}")
    private String key;

    @Autowired
    public NeuroReplacementPilotService(ObjectMapper json) {
        this(json, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(25))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    NeuroReplacementPilotService(ObjectMapper json, HttpClient http) {
        this.json = json;
        this.http = http;
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
        // One payment authorization per room, even after a failed/interrupted task.
        // Return the original checkpoint; never create a second billable job.
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
        // Paid experiments must be downloadable after the game ends.
        // Fail free preflight rather than charging Kling when S3 is missing.
        if (jdbc == null || objectStore == null || !objectStore.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "NEURO_RESULT_ARCHIVE_NOT_CONFIGURED");
        }
        Job job = new Job(UUID.randomUUID().toString(), roomId, ownerUid);
        job.cameraMime=videoMime;
        job.voiceMime=voiceMime;
        job.startSeconds=windowStartSeconds;
        job.sourceCameraKey="neuro-replacements/"+ownerUid+"/"+job.id+"-input-camera"+
                (videoMime.equals("video/mp4")?".mp4":".webm");
        job.sourceVoiceKey="neuro-replacements/"+ownerUid+"/"+job.id+"-input-voice"+
                (voiceMime.contains("wav")?".wav":voiceMime.equals("audio/mp4")?".m4a":".webm");
        try {
            // Paid work MUST NOT start until both references have been archived.
            // Railway restarts can then recover the task using the same inputs.
            if (objectStore == null || !objectStore.configured()) {
                throw new IllegalStateException("NEURO_INPUT_ARCHIVE_NOT_CONFIGURED");
            }
            objectStore.put(job.sourceCameraKey, videoBytes, videoMime);
            objectStore.put(job.sourceVoiceKey, voiceBytes, voiceMime);
            persist(job);
        }
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
        if (!"ready".equals(job.state) || (job.finalVideo == null && job.finalVideoKey == null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "NEURO_REPLACEMENT_NOT_READY");
        }
        try {
            if (job.finalVideoKey != null && objectStore != null && objectStore.configured()) {
                return objectStore.get(job.finalVideoKey);
            }
            return download(job.finalVideo, MAX_RESULT_BYTES);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "NEURO_REPLACEMENT_DOWNLOAD_FAILED");
        }
    }

    private Job find(String roomId, String ownerUid, String jobId) {
        // Resolve from the durable checkpoint first. Cached room jobs may be
        // stale on this Railway instance while another worker progresses.
        Job job = findOwnerJob(ownerUid, jobId);
        if (job == null) job = existingRoomJob(roomId);
        if (job == null || !job.id.equals(jobId) ||
                !job.roomId.equals(roomId) || !job.ownerUid.equals(ownerUid)) {
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
            // A fresh performance is required: video-to-video edit tends
            // to reproduce the original head-scratching gestures. O3
            // reference-to-video instead directs new motion from a 10s
            // appearance/room sample into a new 15s acting sequence.
            // Finish the expensive video step before ordering TTS/voice transfer:
            // do not charge for audio if Kling rejects the input.
            String videoUrl = paidStage(job, VIDEO_MODEL_ID,
                    () -> submitMultipart(VIDEO_MODEL_ID, klingEditFields(),
                            "video_url", "webcam.mp4", "video/mp4", klingCamera));
            job.stage = "Подготовка речи";
            checkpoint(job);
            // "tts" is the durable logical checkpoint key used by legacy
            // paid jobs. New tasks use expressive native-Russian Gemini TTS;
            // old paid TTS IDs are always read with GET, never billed again.
            String tts = paidStage(job, "tts", () -> submitJson(TTS_MODEL_ID, Map.of(
                    "prompt", SPOKEN_TEXT,
                    "voice", "Charon",
                    "style_instructions", SPEECH_STYLE,
                    "language_code", "Russian (Russia)",
                    "model", "gemini-2.5-flash-tts",
                    "output_format", "mp3")));
            job.stage = "Копирование голоса";
            checkpoint(job);
            String voiceUrl = paidStage(job, "chatterbox-speech", () -> submitMultipart(
                    "chatterbox-speech", Map.of("source_audio_url", tts),
                    "target_voice_audio_url", "reference.wav", "audio/wav", chatterboxVoice));
            job.stage = "Синхронизация губ";
            checkpoint(job);
            String finalVideo = paidStage(job, "lipsync", () -> submitJson("lipsync", Map.of(
                    "video_url", videoUrl, "audio_url", voiceUrl,
                    "model", "lipsync-1.9.0-beta", "sync_mode", LIPSYNC_SYNC_MODE)));
            validatedResultUri(finalVideo);
            job.finalVideo = finalVideo;
            if (objectStore != null && objectStore.configured()) {
                byte[] archive = recoverPaidLipSyncVideo(job, finalVideo);
                String archiveKey = "neuro-replacements/" + job.ownerUid + "/" + job.id + ".mp4";
                objectStore.put(archiveKey, archive, "video/mp4");
                job.finalVideoKey = archiveKey;
                checkpoint(job);
            } else {
                LOG.warn("Neuro MP4 durable archive unavailable: S3 not configured, job={}", job.id);
            }
            job.stage = "Готово";
            job.state = "ready";
            checkpoint(job);
            LOG.info("Neuro finished job={} room={} providerRequests={}", job.id, job.roomId, job.providerRequests);
        } catch (Exception error) {
            // Railway shutdown interrupts the polling thread. That is NOT a
            // provider failure: leave a resumable processing checkpoint in
            // PostgreSQL for the next app instance, with the same request ID.
            if (error instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                LOG.info("Neuro worker paused for deployment; will resume from existing GenAPI ID: {}", job.id);
                return;
            }
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
    /** O3 reference-to-video creates a new directed 15s performance. */
    private Map<String, String> klingEditFields() {
        return Map.of(
                "model", VIDEO_ACTING_MODE,
                "prompt", VIDEO_PROMPT,
                "duration", String.valueOf(VIDEO_DURATION_SECONDS),
                "aspect_ratio", "16:9",
                "pro", "true",
                "generate_audio", "false",
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



    /**
     * Recover the ALREADY-PAID Lip Sync output without any new generation.
     * The "cut_off" provider option can shorten a valid 10-second Kling video
     * to the voice duration. Recover the original 10-second timeline by using
     * the paid Kling tail and the paid Chatterbox voice, never new POST requests.
     * Keep strict validation: a malformed/empty video cannot be passed off as
     * a complete replacement.
     */
    private byte[] recoverPaidLipSyncVideo(Job job, String lipSyncUrl) throws Exception {
        byte[] result = download(lipSyncUrl, MAX_RESULT_BYTES);
        MediaProbe media = probeMp4(result);
        if (media.video && media.audio && media.seconds >= 8.0 && media.seconds <= 15.0) {
            verifyPlayableMp4(result);
            return result;
        }
        if (!media.video || media.seconds < 3.0 || media.seconds > 15.0) {
            throw new IOException("NEURO_PAID_LIPSYNC_UNRECOVERABLE video=" + media.video +
                    " audio=" + media.audio + " duration=" + media.seconds);
        }
        String klingId = job.providerRequests.get(VIDEO_MODEL_ID);
        String voiceId = job.providerRequests.get("chatterbox-speech");
        if (klingId == null || voiceId == null) {
            throw new IOException("NEURO_PAID_LIPSYNC_RECOVERY_IDS_MISSING");
        }
        // GET only: these are the original successful provider task IDs.
        String klingUrl = completedPaidResultUrl(VIDEO_MODEL_ID, klingId);
        String voiceUrl = completedPaidResultUrl("chatterbox-speech", voiceId);
        byte[] kling = download(klingUrl, MAX_RESULT_BYTES);
        byte[] voice = download(voiceUrl, MAX_RESULT_BYTES);
        byte[] repaired = composePaidLipSyncWithOriginalTail(result, kling, voice, media.seconds);
        verifyPlayableMp4(repaired);
        LOG.info("Recovered paid Lip Sync without provider POST: job={} lipSyncSeconds={} audioPresent={}",
                job.id, media.seconds, media.audio);
        return repaired;
    }

    private String completedPaidResultUrl(String model, String id) throws Exception {
        JsonNode data = send(baseRequest(GENAPI.resolve("request/get/" + id)).GET().build());
        String status = data.path("status").asText("");
        if (!"success".equalsIgnoreCase(status) && !"completed".equalsIgnoreCase(status)) {
            throw new IOException("NEURO_RECOVERY_ORIGINAL_NOT_READY " + model + " " + status);
        }
        JsonNode output = data.path("result");
        String url = output.isArray() && !output.isEmpty() ?
                output.get(0).asText("") : output.asText("");
        validatedResultUri(url);
        return url;
    }

    /**
     * Keep the actual lip-synced opening, append the remaining edited Kling
     * gestures when cut_off shortened it, and retain the exact Chatterbox
     * voice provided to Lip Sync. No synthesis or billable remote operation.
     */
    private byte[] composePaidLipSyncWithOriginalTail(byte[] lipSync, byte[] kling,
                                                       byte[] voice, double lipSeconds) throws Exception {
        Path lip = Files.createTempFile("neuro-paid-lipsync-", ".mp4");
        Path klingFile = Files.createTempFile("neuro-paid-kling-", ".mp4");
        Path voiceFile = Files.createTempFile("neuro-paid-voice-", ".audio");
        Path output = Files.createTempFile("neuro-paid-recovered-", ".mp4");
        try {
            Files.write(lip, lipSync);
            Files.write(klingFile, kling);
            Files.write(voiceFile, voice);
            double lead = Math.min(10.0, lipSeconds);
            String standardize = "fps=24,scale=1280:720:force_original_aspect_ratio=decrease,"
                    + "pad=1280:720:(ow-iw)/2:(oh-ih)/2,setsar=1,format=yuv420p,";
            String filter;
            if (lead < 9.8) {
                filter = "[0:v]" + standardize + "trim=duration=" + lead +
                        ",setpts=PTS-STARTPTS[lead];" +
                        "[1:v]" + standardize + "trim=start=" + lead +
                        ":duration=" + (10.0 - lead) + ",setpts=PTS-STARTPTS[tail];" +
                        "[lead][tail]concat=n=2:v=1:a=0," +
                        "tpad=stop_mode=clone:stop_duration=10,trim=duration=10[v];" +
                        "[2:a]aresample=48000,apad,atrim=duration=10[a]";
            } else {
                filter = "[0:v]" + standardize +
                        "tpad=stop_mode=clone:stop_duration=10,trim=duration=10,setpts=PTS-STARTPTS[v];" +
                        "[2:a]aresample=48000,apad,atrim=duration=10[a]";
            }
            List<String> command = new ArrayList<>(List.of(
                    "ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
                    "-i", lip.toString(), "-i", klingFile.toString(), "-i", voiceFile.toString(),
                    "-filter_complex", filter,
                    "-map", "[v]", "-map", "[a]", "-t", "10",
                    "-threads", "1", "-filter_complex_threads", "1",
                    "-c:v", "libx264", "-preset", "ultrafast", "-crf", "20", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", output.toString()));
            Process tool = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!tool.waitFor(120, TimeUnit.SECONDS)) {
                tool.destroyForcibly();
                throw new IOException("NEURO_PAID_LIPSYNC_MUX_TIMEOUT");
            }
            if (tool.exitValue() != 0) {
                String details = new String(tool.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String safe = details.replaceAll("[^A-Za-z0-9 _.,:;!()#-]", " ").trim();
                throw new IOException("NEURO_PAID_LIPSYNC_MUX_FAILED " +
                        safe.substring(0, Math.min(180, safe.length())));
            }
            long bytes = Files.size(output);
            if (bytes <= 256 || bytes > MAX_RESULT_BYTES) {
                throw new IOException("NEURO_PAID_LIPSYNC_MUX_SIZE_INVALID " + bytes);
            }
            return Files.readAllBytes(output);
        } finally {
            Files.deleteIfExists(lip);
            Files.deleteIfExists(klingFile);
            Files.deleteIfExists(voiceFile);
            Files.deleteIfExists(output);
        }
    }

    private record MediaProbe(boolean video, boolean audio, double seconds) {}

    private MediaProbe probeMp4(byte[] data) throws Exception {
        if (data == null || data.length < 256) throw new IOException("NEURO_RESULT_EMPTY");
        Path file = Files.createTempFile("neuro-result-check-", ".mp4");
        try {
            Files.write(file, data);
            Process probe = new ProcessBuilder("ffprobe", "-v", "error",
                    "-show_entries", "stream=codec_type",
                    "-show_entries", "format=duration",
                    "-of", "json", file.toString())
                    .redirectErrorStream(true).start();
            if (!probe.waitFor(15, TimeUnit.SECONDS)) {
                probe.destroyForcibly();
                throw new IOException("NEURO_RESULT_MEDIA_PROBE_TIMEOUT");
            }
            if (probe.exitValue() != 0) throw new IOException("NEURO_RESULT_INVALID_MP4");
            JsonNode info = json.readTree(probe.getInputStream().readAllBytes());
            boolean video = false, audio = false;
            for (JsonNode stream : info.path("streams")) {
                video |= "video".equals(stream.path("codec_type").asText());
                audio |= "audio".equals(stream.path("codec_type").asText());
            }
            double duration = info.path("format").path("duration").asDouble(0);
            return new MediaProbe(video, audio, duration);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /**
     * Never announce a completed AI replacement for a broken/silent MP4.
     * The synced clip must contain both playable video and an audible track.
     * This check is local and free; it never calls GenAPI.
     */
    private void verifyPlayableMp4(byte[] data) throws Exception {
        MediaProbe info = probeMp4(data);
        if (!info.video || !info.audio || info.seconds < 8.0 || info.seconds > 15.0) {
            throw new IOException("NEURO_RESULT_MISSING_VIDEO_AUDIO_OR_DURATION video=" +
                    info.video + " audio=" + info.audio + " duration=" + info.seconds);
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

    /**
     * Exactly-once guard for each billable stage: after a deploy use the saved
     * GenAPI request ID and GET its result; never submit a second paid POST.
     * Persist an ambiguous-submission checkpoint BEFORE sending a paid POST.
     */
    private String paidStage(Job job, String model, Callable<String> submit) throws Exception {
        String existing = job.providerRequests.get(model);
        if (existing != null && !existing.isBlank()) return awaitResult(job, model, existing);
        if (job.submittingModel != null) {
            throw new IOException("PAID_SUBMISSION_UNCERTAIN_"+job.submittingModel+
                    " — automatic re-submission forbidden");
        }
        job.submittingModel = model;
        persist(job);
        String id = submit.call();
        // Store provider ID BEFORE any polling or additional paid operation.
        job.providerRequests.put(model, id);
        job.submittingModel = null;
        persist(job);
        return awaitResult(job, model, id);
    }

    private String awaitResult(Job job, String model, String id) throws Exception {
        job.providerRequests.put(model, id);
        // This ID is the durable guard against a duplicate paid submission.
        persist(job);
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
    public void close() {
        executor.shutdownNow();
        recoveryExecutor.shutdownNow();
    }

    private static final class Job {
        final String id;
        final String roomId;
        final String ownerUid;
        volatile String state = "queued";
        volatile String stage = "В очереди";
        volatile String finalVideo;
        volatile String finalVideoKey;
        volatile String error;
        volatile String sourceCameraKey;
        volatile String sourceVoiceKey;
        volatile String cameraMime;
        volatile String voiceMime;
        volatile double startSeconds;
        volatile String submittingModel;
        final Map<String, String> providerRequests = new ConcurrentHashMap<>();

        Job(String id, String roomId, String ownerUid) {
            this.id = id;
            this.roomId = roomId;
            this.ownerUid = ownerUid;
        }
    }
}
