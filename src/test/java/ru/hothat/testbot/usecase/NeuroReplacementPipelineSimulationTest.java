package ru.hothat.testbot.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.sql.ResultSet;
import org.springframework.test.util.ReflectionTestUtils;
import ru.hothat.common.storage.ObjectStore;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

/**
 * The real pipeline (including local FFmpeg preparation) with a simulated
 * provider: zero GenAPI access, zero spend. Verifies all four billable stages,
 * final archive, owner status, and no second paid submission.
 */
class NeuroReplacementPipelineSimulationTest {
    private NeuroReplacementPilotService pilot;

    @AfterEach
    void close() {
        if (pilot != null) pilot.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void fullFourStageFlowStoresFinalMp4AndNeverSubmitsTwice() throws Exception {
        byte[] camera = generatedMedia(".webm", List.of(
                "-f", "lavfi", "-i", "testsrc2=size=320x180:rate=15",
                "-t", "10", "-an", "-c:v", "libvpx", "-deadline", "realtime",
                "-b:v", "160k"));
        byte[] voice = generatedMedia(".wav", List.of(
                "-f", "lavfi", "-i", "sine=frequency=500:sample_rate=16000",
                "-t", "10", "-ac", "1", "-c:a", "pcm_s16le"));
        byte[] finalMp4 = generatedMedia(".mp4", List.of(
                "-f", "lavfi", "-i", "color=c=blue:size=320x180:rate=24",
                "-f", "lavfi", "-i", "sine=frequency=500:sample_rate=16000",
                "-t", "10", "-c:v", "libx264", "-preset", "ultrafast",
                "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest"));

        List<String> paid = new ArrayList<>();
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(call -> {
                    HttpRequest request = call.getArgument(0);
                    String path = request.uri().getPath();
                    if ("POST".equals(request.method())) {
                        assertTrue(path.startsWith("/api/v1/networks/"));
                        String model = path.substring(path.lastIndexOf('/') + 1);
                        paid.add(model);
                        return response("{\"request_id\":\"sim-" + model + "\"}");
                    }
                    if (path.startsWith("/api/v1/request/get/sim-")) {
                        String model = path.substring(path.lastIndexOf("sim-") + 4);
                        String extension = model.equals("tts") ? "mp3"
                                : model.equals("chatterbox-speech") ? "wav" : "mp4";
                        String url = "https://gen-api.storage.yandexcloud.net/sim-" + model + "." + extension;
                        return response("{\"status\":\"success\",\"result\":[\"" + url + "\"]}");
                    }
                    if ("gen-api.storage.yandexcloud.net".equals(request.uri().getHost())) {
                        assertTrue(path.endsWith("sim-lipsync.mp4"),
                                "only the final lip-synced video is downloaded for archive");
                        return response(finalMp4);
                    }
                    throw new IOException("Unexpected provider call: " + request.uri());
                });
        pilot = new NeuroReplacementPilotService(new ObjectMapper(), client);
        JdbcTemplate database = mock(JdbcTemplate.class);
        ObjectStore archive = mock(ObjectStore.class);
        when(archive.configured()).thenReturn(true);
        pilot.setJdbcTemplate(database);
        pilot.setObjectStore(archive);
        ReflectionTestUtils.setField(pilot, "enabled", true);
        ReflectionTestUtils.setField(pilot, "key", "simulation-only-key");

        String room = "hat-0123456789abcdef";
        String owner = "owner-1";
        Object job = newJob("simulation-job", room, owner);
        Map<String, Object> rooms = (Map<String, Object>) ReflectionTestUtils.getField(pilot, "byRoom");
        rooms.put(room, job);

        ReflectionTestUtils.invokeMethod(pilot, "process",
                job, camera, "video/webm", voice, "audio/wav", 0.0);

        Map<String, Object> status = pilot.status(room, owner, "simulation-job");
        assertEquals("ready", status.get("status"), status.toString());
        assertEquals(Boolean.TRUE, status.get("ready"));
        assertEquals(List.of("kling-video-o3", "tts", "chatterbox-speech", "lipsync"),
                paid, "no model stage is silently omitted or reordered");
        assertEquals(4, ((Map<?, ?>) status.get("providerRequests")).size());
        verify(archive, times(1)).put(eq("neuro-replacements/owner-1/simulation-job.mp4"),
                aryEq(finalMp4), eq("video/mp4"));

        // A user retry in the same room must return the existing result,
        // without a fifth paid submission and without even examining media.
        Map<String, Object> duplicate = pilot.start(room, owner, true, null, null);
        assertEquals("simulation-job", duplicate.get("jobId"));
        assertEquals(4, paid.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void restartedStageWithSavedProviderIdPerformsOnlyFreeGet() throws Exception {
        List<String> requests = new ArrayList<>();
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(call -> {
                    HttpRequest request = call.getArgument(0);
                    requests.add(request.method() + " " + request.uri().getPath());
                    assertEquals("GET", request.method(), "recovery must not submit another paid POST");
                    return response("{\"status\" : \"success\","
                            + "\"result\":[\"https://gen-api.storage.yandexcloud.net/resumed.mp4\"]}");
                });
        pilot = new NeuroReplacementPilotService(new ObjectMapper(), client);
        pilot.setJdbcTemplate(mock(JdbcTemplate.class));
        Object job = newJob("existing-id", "hat-0123456789abcdef", "owner-1");
        Map<String, String> refs = (Map<String, String>) ReflectionTestUtils.getField(job, "providerRequests");
        refs.put("kling-video-o3", "already-paid-request");
        ReflectionTestUtils.setField(pilot, "key", "simulation-only-key");
        Callable<String> forbiddenSubmit = () -> { fail("duplicate paid POST attempted"); return ""; };

        String url = ReflectionTestUtils.invokeMethod(
                pilot, "paidStage", job, "kling-video-o3", forbiddenSubmit);
        assertEquals("https://gen-api.storage.yandexcloud.net/resumed.mp4", url);
        assertEquals(List.of("GET /api/v1/request/get/already-paid-request"), requests);
    }

    @Test
    void videoWithoutSynthesizedSpeechIsNeverAcceptedAsReady() throws Exception {
        byte[] silent = generatedMedia(".mp4", List.of(
                "-f", "lavfi", "-i", "color=c=black:size=320x180:rate=24",
                "-t", "10", "-an", "-c:v", "libx264", "-preset", "ultrafast"));
        pilot = new NeuroReplacementPilotService(new ObjectMapper());
        assertThrows(Exception.class,
                () -> ReflectionTestUtils.invokeMethod(pilot, "verifyPlayableMp4", silent),
                "A silent Kling clip is not a completed voice-and-lips Neuro replacement");
    }


    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void resumedAfterDefinite402UsesExistingThreePaidIdsAndOnlySubmitsLipSyncOnce() throws Exception {
        byte[] camera = generatedMedia(".webm", List.of(
                "-f", "lavfi", "-i", "testsrc2=size=320x180:rate=15",
                "-t", "10", "-an", "-c:v", "libvpx", "-deadline", "realtime",
                "-b:v", "160k"));
        byte[] voice = generatedMedia(".wav", List.of(
                "-f", "lavfi", "-i", "sine=frequency=500:sample_rate=16000",
                "-t", "10", "-ac", "1", "-c:a", "pcm_s16le"));
        byte[] finalMp4 = generatedMedia(".mp4", List.of(
                "-f", "lavfi", "-i", "color=c=blue:size=320x180:rate=24",
                "-f", "lavfi", "-i", "sine=frequency=500:sample_rate=16000",
                "-t", "10", "-c:v", "libx264", "-preset", "ultrafast",
                "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest"));

        List<String> paid = new ArrayList<>();
        List<String> inspected = new ArrayList<>();
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class)))
                .thenAnswer(call -> {
                    HttpRequest req = call.getArgument(0);
                    String path = req.uri().getPath();
                    if ("POST".equals(req.method())) {
                        String model = path.substring(path.lastIndexOf('/') + 1);
                        assertEquals("lipsync", model, "previous paid stages must never be resubmitted");
                        paid.add(model);
                        return response("{\"request_id\":\"sim-lipsync\"}");
                    }
                    if (path.startsWith("/api/v1/request/get/")) {
                        String id = path.substring(path.lastIndexOf('/') + 1);
                        inspected.add(id);
                        return response("{\"status\":\"success\",\"result\":[\"https://gen-api.storage.yandexcloud.net/"
                                + id + ".mp4\"]}");
                    }
                    if ("gen-api.storage.yandexcloud.net".equals(req.uri().getHost())) {
                        return response(finalMp4);
                    }
                    throw new IOException("Unexpected provider request: " + req.uri());
                });
        pilot = new NeuroReplacementPilotService(new ObjectMapper(), client);
        ReflectionTestUtils.setField(pilot, "enabled", true);
        ReflectionTestUtils.setField(pilot, "key", "simulated-key");

        String id = "402-original-job", owner = "owner-1", room = "hat-0123456789abcdef";
        JdbcTemplate db = mock(JdbcTemplate.class);
        ResultSet saved = mock(ResultSet.class);
        when(saved.getString(1)).thenReturn(id);
        when(saved.getString(2)).thenReturn(room);
        when(saved.getString(3)).thenReturn(owner);
        when(saved.getString(4)).thenReturn("failed");
        when(saved.getString(5)).thenReturn("Ошибка генерации");
        when(saved.getString(6)).thenReturn(
                "{\"kling-video-o3\":\"56854976\",\"tts\":\"56855655\",\"chatterbox-speech\":\"56855661\"}");
        when(saved.getString(8)).thenReturn("GENAPI_HTTP_402 Исчерпан лимит расходов по API-ключу");
        when(saved.getString(10)).thenReturn("neuro-replacements/owner-1/input-camera.webm");
        when(saved.getString(11)).thenReturn("neuro-replacements/owner-1/input-voice.wav");
        when(saved.getString(12)).thenReturn("video/webm");
        when(saved.getString(13)).thenReturn("audio/wav");
        when(saved.getString(15)).thenReturn("lipsync");
        when(db.query(anyString(), any(RowMapper.class), eq(id)))
                .thenAnswer(call -> List.of(((RowMapper)call.getArgument(1)).mapRow(saved,0)));
        when(db.update(anyString(),eq(id),eq(owner))).thenReturn(1,0);
        pilot.setJdbcTemplate(db);
        ObjectStore archive = mock(ObjectStore.class);
        when(archive.configured()).thenReturn(true);
        when(archive.get("neuro-replacements/owner-1/input-camera.webm")).thenReturn(camera);
        when(archive.get("neuro-replacements/owner-1/input-voice.wav")).thenReturn(voice);
        pilot.setObjectStore(archive);

        ResponseStatusException noConsent = assertThrows(ResponseStatusException.class,
                () -> pilot.resumeAfterLimit(owner,id,false));
        assertEquals(HttpStatus.BAD_REQUEST,noConsent.getStatusCode());
        assertEquals(List.of(),paid);

        Map<String,Object> resumed = pilot.resumeAfterLimit(owner,id,true);
        assertEquals("processing",resumed.get("status"));
        // A double click must fail at the conditional claim, even across instances.
        ResponseStatusException again = assertThrows(ResponseStatusException.class,
                () -> pilot.resumeAfterLimit(owner,id,true));
        assertEquals(HttpStatus.CONFLICT,again.getStatusCode());

        Map<String,Object> rooms = (Map<String,Object>)ReflectionTestUtils.getField(pilot,"byRoom");
        Object job = rooms.get(room);
        long until = System.nanoTime()+TimeUnit.SECONDS.toNanos(35);
        while(System.nanoTime()<until && "processing".equals(ReflectionTestUtils.getField(job,"state"))) {
            Thread.sleep(100);
        }
        assertEquals("ready",ReflectionTestUtils.getField(job,"state"),
                String.valueOf(ReflectionTestUtils.getField(job,"error")));
        assertEquals(List.of("lipsync"),paid);
        assertEquals(List.of("56854976","56855655","56855661","sim-lipsync"),inspected);
        verify(archive).put(eq("neuro-replacements/owner-1/402-original-job.mp4"),
                aryEq(finalMp4),eq("video/mp4"));
    }


    @Test
    void alreadyPaidCutOffVideoShorterThanEightSecondsIsRestoredToTenWithoutPaidPosts() throws Exception {
        assertRecoveryFromPaidStages(false);
    }

    @Test
    void alreadyPaidSilentLipSyncVideoIsMuxedWithSavedChatterboxVoiceForTenSeconds() throws Exception {
        assertRecoveryFromPaidStages(true);
    }

    @SuppressWarnings("unchecked")
    private void assertRecoveryFromPaidStages(boolean missingAudio) throws Exception {
        byte[] shortened = generatedMedia(".mp4", missingAudio ? List.of(
                "-f", "lavfi", "-i", "color=c=blue:size=320x180:rate=24",
                "-t", "6", "-an", "-c:v", "libx264", "-preset", "ultrafast",
                "-pix_fmt", "yuv420p") : List.of(
                "-f", "lavfi", "-i", "color=c=blue:size=320x180:rate=24",
                "-f", "lavfi", "-i", "sine=frequency=550:sample_rate=16000",
                "-t", "6", "-c:v", "libx264", "-preset", "ultrafast",
                "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest"));
        byte[] kling = generatedMedia(".mp4", List.of(
                "-f", "lavfi", "-i", "color=c=red:size=320x180:rate=24",
                "-t", "10", "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p"));
        byte[] voice = generatedMedia(".wav", List.of(
                "-f", "lavfi", "-i", "sine=frequency=450:sample_rate=24000",
                "-t", "6", "-ac", "1", "-c:a", "pcm_s16le"));
        List<String> requested = new ArrayList<>();
        HttpClient provider = mock(HttpClient.class);
        when(provider.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class)))
                .thenAnswer(call -> {
                    HttpRequest request = call.getArgument(0);
                    assertEquals("GET",request.method(), "no paid POST while recovering successful paid IDs");
                    String path = request.uri().getPath();
                    requested.add(path);
                    if (path.endsWith("/paid-lipsync.mp4")) return response(shortened);
                    if (path.endsWith("/paid-kling.mp4")) return response(kling);
                    if (path.endsWith("/paid-voice.wav")) return response(voice);
                    if (path.endsWith("/request/get/kling-paid")) {
                        return response("{\"status\":\"success\",\"result\":[\"https://gen-api.storage.yandexcloud.net/paid-kling.mp4\"]}");
                    }
                    if (path.endsWith("/request/get/voice-paid")) {
                        return response("{\"status\":\"success\",\"result\":[\"https://gen-api.storage.yandexcloud.net/paid-voice.wav\"]}");
                    }
                    throw new IOException("Unexpected paid recovery HTTP call: "+path);
                });
        pilot = new NeuroReplacementPilotService(new ObjectMapper(),provider);
        ReflectionTestUtils.setField(pilot,"key","simulated-api-key");
        Object job = newJob("already-paid", "hat-a1ece4688a6e8389","owner-1");
        Map<String,String> requests = (Map<String,String>)ReflectionTestUtils.getField(job,"providerRequests");
        requests.put("kling-video-o3","kling-paid");
        requests.put("chatterbox-speech","voice-paid");
        requests.put("lipsync","56862413");
        byte[] normalized = ReflectionTestUtils.invokeMethod(pilot,"recoverPaidLipSyncVideo",job,
                "https://gen-api.storage.yandexcloud.net/paid-lipsync.mp4");
        assertNotNull(normalized);
        assertTrue(normalized.length>1000);
        ReflectionTestUtils.invokeMethod(pilot,"verifyPlayableMp4",normalized);
        assertEquals(5,requested.size());
        assertTrue(requested.contains("/paid-lipsync.mp4"));
        assertTrue(requested.stream().anyMatch(p -> p.endsWith("/request/get/kling-paid")));
        assertTrue(requested.stream().anyMatch(p -> p.endsWith("/request/get/voice-paid")));
    }

    private static Object newJob(String id, String room, String owner) throws Exception {
        Class<?> type = Class.forName(NeuroReplacementPilotService.class.getName() + "$Job");
        var ctor = type.getDeclaredConstructor(String.class, String.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(id, room, owner);
    }

    private static byte[] generatedMedia(String suffix, List<String> input) throws Exception {
        Path file = Files.createTempFile("neuro-sim-", suffix);
        try {
            List<String> command = new ArrayList<>();
            command.addAll(List.of("ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-y"));
            command.addAll(input);
            command.add(file.toString());
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                fail("Timed out generating synthetic fixture");
            }
            assertEquals(0, process.exitValue(),
                    "Could not generate test media: " + new String(process.getInputStream().readAllBytes()));
            return Files.readAllBytes(file);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static HttpResponse<?> response(Object body) {
        HttpResponse result = mock(HttpResponse.class);
        when(result.statusCode()).thenReturn(200);
        doReturn(body).when(result).body();
        return result;
    }
}
