package ru.hothat.testbot.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
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
                    () -> "Could not generate test media: " + new String(process.getInputStream().readAllBytes()));
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
