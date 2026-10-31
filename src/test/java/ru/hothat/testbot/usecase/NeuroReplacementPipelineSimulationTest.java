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
        byte[] directedPerformance = generatedMedia(".mp4", List.of(
                "-f", "lavfi", "-i", "color=c=red:size=320x180:rate=24",
                "-t", "15", "-an", "-c:v", "libx264", "-preset", "ultrafast",
                "-pix_fmt", "yuv420p"));
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
                        String extension = model.equals("gemini-tts") ? "mp3"
                                : model.equals("chatterbox-speech") ? "wav" : "mp4";
                        String url = "https://gen-api.storage.yandexcloud.net/sim-" + model + "." + extension;
                        return response("{\"status\":\"success\",\"result\":[\"" + url + "\"]}");
                    }
                    if ("gen-api.storage.yandexcloud.net".equals(request.uri().getHost())) {
                        if (path.endsWith("sim-chatterbox-speech.wav")) {
                            return response(voice); // free speech duration preflight
                        }
                        if (path.endsWith("sim-kling-video-o3.mp4")) {
                            return response(directedPerformance); // free 15s acting validation
                        }
                        assertTrue(path.endsWith("sim-lipsync.mp4"),
                                "only the final lip-synced video is downloaded for archive");
                        return response(finalMp4);
                    }
                    throw new IOException("Unexpected provider call: " + request.uri());
                });
        pilot = new NeuroReplacementPilotService(new ObjectMapper(), client);
        ReflectionTestUtils.setField(pilot,"referencePublicOrigin","https://hot-hat-back.up.railway.app");
        JdbcTemplate database = mock(JdbcTemplate.class);
        ObjectStore archive = mock(ObjectStore.class);
        when(archive.configured()).thenReturn(true);
        when(archive.presignGet(anyString(),any(java.time.Duration.class),
                eq("audio/wav"),isNull(),anyString()))
                .thenReturn("https://signed.example.test/valid-audio.wav");
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
        assertEquals(List.of("gemini-tts", "chatterbox-speech", "kling-video-o3", "lipsync"),
                paid, "check expressive Russian speech before expensive video");
        assertEquals(4, ((Map<?, ?>) status.get("providerRequests")).size());
        verify(archive, times(1)).put(eq("neuro-replacements/owner-1/simulation-job.mp4"),
                aryEq(finalMp4), eq("video/mp4"));
        verify(archive).put(eq("neuro-replacements/owner-1/simulation-job-lipsync-voice.wav"),
                any(byte[].class),eq("audio/wav"));

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
        ReflectionTestUtils.setField(pilot,"referencePublicOrigin","https://hot-hat-back.up.railway.app");
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
        ReflectionTestUtils.setField(pilot,"referencePublicOrigin","https://hot-hat-back.up.railway.app");
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
        when(archive.presignGet(anyString(),any(java.time.Duration.class),
                eq("audio/wav"),isNull(),anyString()))
                .thenReturn("https://signed.example.test/valid-audio.wav");
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
        assertEquals(List.of("56855655","56855661","56854976","sim-lipsync"),inspected);
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


    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void checkingCompletedPaidProviderStatusDoesNotDownloadOrReencodeMp4() throws Exception {
        HttpClient provider = mock(HttpClient.class);
        List<String> visited = new ArrayList<>();
        when(provider.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class)))
                .thenAnswer(call -> {
                    HttpRequest request = call.getArgument(0);
                    assertEquals("GET",request.method(), "status must never charge the user");
                    assertTrue(request.uri().getPath().startsWith("/api/v1/request/get/"),
                            "status must not fetch large result files or run FFmpeg");
                    visited.add(request.uri().getPath());
                    return response("{\"status\":\"success\",\"progress\":100,"
                            + "\"result\":[\"https://gen-api.storage.yandexcloud.net/paid.mp4\"]}");
                });
        pilot = new NeuroReplacementPilotService(new ObjectMapper(), provider);
        ReflectionTestUtils.setField(pilot,"key","local-test");
        JdbcTemplate db=mock(JdbcTemplate.class);
        ResultSet saved=mock(ResultSet.class);
        when(saved.getString(1)).thenReturn("existing-paid");
        when(saved.getString(2)).thenReturn("hat-a1ece4688a6e8389");
        when(saved.getString(3)).thenReturn("owner-1");
        when(saved.getString(4)).thenReturn("failed");
        when(saved.getString(5)).thenReturn("Ошибка генерации");
        when(saved.getString(6)).thenReturn(
                "{\"kling-video-o3\":\"56854976\",\"tts\":\"56855655\","
                + "\"chatterbox-speech\":\"56855661\",\"lipsync\":\"56862413\"}");
        when(saved.getString(8)).thenReturn("NEURO_RESULT_MISSING_VIDEO_AUDIO_OR_DURATION");
        when(db.query(anyString(),any(RowMapper.class),eq("existing-paid")))
                .thenAnswer(call -> List.of(((RowMapper)call.getArgument(1)).mapRow(saved,0)));
        pilot.setJdbcTemplate(db);
        ObjectStore storage=mock(ObjectStore.class);
        when(storage.configured()).thenReturn(true);
        pilot.setObjectStore(storage);

        Map<String,Object> info=pilot.inspectProvider("owner-1","existing-paid");
        assertEquals("failed",info.get("status"));
        assertTrue(String.valueOf(info.get("note")).contains("без оплаты"));
        assertEquals(4,visited.size());
        verify(storage,never()).put(anyString(),any(byte[].class),anyString());
        verify(db,never()).update(anyString(),any(Object[].class));
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void freeAsyncRecoveryClaimsOnceAndReusesAllFourPaidRequestIds() throws Exception {
        byte[] lip=generatedMedia(".mp4",List.of(
                "-f","lavfi","-i","color=c=blue:size=320x180:rate=24",
                "-t","6","-an","-c:v","libx264","-preset","ultrafast"));
        byte[] kling=generatedMedia(".mp4",List.of(
                "-f","lavfi","-i","color=c=red:size=320x180:rate=24",
                "-t","10","-an","-c:v","libx264","-preset","ultrafast"));
        byte[] voice=generatedMedia(".wav",List.of(
                "-f","lavfi","-i","sine=frequency=440:sample_rate=24000",
                "-t","6","-ac","1","-c:a","pcm_s16le"));
        List<String> requested=java.util.Collections.synchronizedList(new ArrayList<>());
        HttpClient provider=mock(HttpClient.class);
        when(provider.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class)))
                .thenAnswer(call -> {
                    HttpRequest request=call.getArgument(0);
                    assertEquals("GET",request.method(),"free restoration cannot submit paid POST");
                    String path=request.uri().getPath();
                    requested.add(path);
                    if(path.endsWith("/request/get/56862413"))
                        return response("{\"status\":\"success\",\"result\":[\"https://gen-api.storage.yandexcloud.net/paid-lipsync.mp4\"]}");
                    if(path.endsWith("/request/get/56854976"))
                        return response("{\"status\":\"success\",\"result\":[\"https://gen-api.storage.yandexcloud.net/paid-kling.mp4\"]}");
                    if(path.endsWith("/request/get/56855661"))
                        return response("{\"status\":\"success\",\"result\":[\"https://gen-api.storage.yandexcloud.net/paid-voice.wav\"]}");
                    if(path.endsWith("/paid-lipsync.mp4")) return response(lip);
                    if(path.endsWith("/paid-kling.mp4")) return response(kling);
                    if(path.endsWith("/paid-voice.wav")) return response(voice);
                    throw new IOException("Unexpected free recovery request: "+path);
                });
        pilot=new NeuroReplacementPilotService(new ObjectMapper(),provider);
        ReflectionTestUtils.setField(pilot,"key","test-key");
        String jobId="existing-paid",owner="owner-1",room="hat-a1ece4688a6e8389";
        JdbcTemplate db=mock(JdbcTemplate.class);
        ResultSet saved=mock(ResultSet.class);
        when(saved.getString(1)).thenReturn(jobId);
        when(saved.getString(2)).thenReturn(room);
        when(saved.getString(3)).thenReturn(owner);
        when(saved.getString(4)).thenReturn("failed");
        when(saved.getString(5)).thenReturn("Ошибка генерации");
        when(saved.getString(6)).thenReturn(
                "{\"kling-video-o3\":\"56854976\",\"tts\":\"56855655\","
                + "\"chatterbox-speech\":\"56855661\",\"lipsync\":\"56862413\"}");
        when(saved.getString(8)).thenReturn("NEURO_RESULT_MISSING_VIDEO_AUDIO_OR_DURATION");
        when(db.query(anyString(),any(RowMapper.class),eq(jobId)))
                .thenAnswer(call -> List.of(((RowMapper)call.getArgument(1)).mapRow(saved,0)));
        when(db.update(anyString(),eq(jobId),eq(owner))).thenReturn(1,0);
        pilot.setJdbcTemplate(db);
        ObjectStore storage=mock(ObjectStore.class);
        when(storage.configured()).thenReturn(true);
        pilot.setObjectStore(storage);

        Map<String,Object> accepted=pilot.recoverPaidOutput(owner,jobId);
        assertEquals(jobId,accepted.get("jobId"));
        assertThrows(ResponseStatusException.class,()->pilot.recoverPaidOutput(owner,jobId),
                "Second click or another instance must not start a second repair job");
        Map<String,Object> cached=(Map<String,Object>)ReflectionTestUtils.getField(pilot,"byRoom");
        Object job=cached.get(room);
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);
        while(System.nanoTime()<until &&
                "processing".equals(ReflectionTestUtils.getField(job,"state"))) Thread.sleep(100);
        assertEquals("ready",ReflectionTestUtils.getField(job,"state"),
                String.valueOf(ReflectionTestUtils.getField(job,"error")));
        verify(storage,times(1)).put(eq("neuro-replacements/owner-1/existing-paid.mp4"),
                any(byte[].class),eq("video/mp4"));
        assertEquals(6,requested.size(),"All HTTP calls are free GETs of paid results");
    }

    @Test
    void rejectsRushedTwoPointSixSecondSpeechBeforePayingForVideo() throws Exception {
        byte[] rushed = generatedMedia(".wav", List.of(
                "-f", "lavfi", "-i", "sine=frequency=500:sample_rate=24000",
                "-t", "2.6", "-ac", "1", "-c:a", "pcm_s16le"));
        HttpClient provider=mock(HttpClient.class);
        when(provider.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class)))
                .thenAnswer(call -> {
                    HttpRequest req=call.getArgument(0);
                    assertEquals("GET",req.method(),"the voice quality check must not bill GenAPI");
                    return response(rushed);
                });
        pilot=new NeuroReplacementPilotService(new ObjectMapper(),provider);
        ReflectionTestUtils.setField(pilot,"key","simulation");
        Exception e=assertThrows(Exception.class,() ->
                ReflectionTestUtils.invokeMethod(pilot,"verifyExpressiveSpeechDuration",
                        "https://gen-api.storage.yandexcloud.net/rushed.wav"));
        assertTrue(String.valueOf(e.getMessage()).contains("NEURO_SPEECH_TOO_FAST_OR_SILENT")
                || (e.getCause()!=null && String.valueOf(e.getCause().getMessage()).contains("NEURO_SPEECH_TOO_FAST_OR_SILENT")));
    }

    @Test
    void dynamicNineSecondSceneAcceptsSpeechThatEndsAtSevenSecondsWithTrailingSilence() throws Exception {
        // The 31-Oct provider really returned 5.2 voiced seconds, ending at
        // 6.9s; its padded audio was wrongly rejected by a file-length gate.
        byte[] paddedSpeech = generatedMedia(".wav", List.of(
                "-f", "lavfi", "-i", "sine=frequency=470:sample_rate=24000:duration=7",
                "-af", "apad=pad_dur=5", "-t", "12",
                "-ac", "1", "-c:a", "pcm_s16le"));
        HttpClient provider = mock(HttpClient.class);
        when(provider.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class)))
                .thenAnswer(call -> {
                    HttpRequest request = call.getArgument(0);
                    assertEquals("GET",request.method(),"timing check must never generate or bill");
                    return response(paddedSpeech);
                });
        pilot = new NeuroReplacementPilotService(new ObjectMapper(),provider);
        ReflectionTestUtils.setField(pilot,"key","simulation");
        NeuroScenePlan scene = NeuroScenePlan.plan(
                "Повернуться к веб-камере и смотреть прямо в камеру. "
                + "Выразительно произнести «А вы всё ещё кипятите?», выдержать паузу, "
                + "затем выразительно произнести «Тогда мы идём к вам!», "
                + "показать большой палец прямо в камеру. "
                + "Затем угрожающе погрозить кулаком прямо в камеру, встать со стула и уйти из кадра.");
        assertEquals(9,scene.durationSeconds());
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(pilot,
                "verifyExpressiveSpeechDuration",
                "https://gen-api.storage.yandexcloud.net/voice-with-silence.wav",scene));
    }

    @Test
    void dynamicNineSecondSceneStillRejectsOverlyRushedSpeech() throws Exception {
        byte[] rushed = generatedMedia(".wav", List.of(
                "-f", "lavfi", "-i", "sine=frequency=470:sample_rate=24000",
                "-t", "2.6", "-ac", "1", "-c:a", "pcm_s16le"));
        HttpClient provider = mock(HttpClient.class);
        when(provider.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class)))
                .thenAnswer(call -> response(rushed));
        pilot = new NeuroReplacementPilotService(new ObjectMapper(),provider);
        ReflectionTestUtils.setField(pilot,"key","simulation");
        NeuroScenePlan scene = NeuroScenePlan.plan(
                "Повернуться к веб-камере и смотреть прямо в камеру. "
                + "Выразительно произнести «А вы всё ещё кипятите?», выдержать паузу, "
                + "затем выразительно произнести «Тогда мы идём к вам!», "
                + "показать большой палец прямо в камеру. "
                + "Затем угрожающе погрозить кулаком прямо в камеру, встать со стула и уйти из кадра.");
        Exception error = assertThrows(Exception.class, () ->
                ReflectionTestUtils.invokeMethod(pilot,"verifyExpressiveSpeechDuration",
                    "https://gen-api.storage.yandexcloud.net/rushed.wav",scene));
        assertTrue(String.valueOf(error).contains("NEURO_SPEECH_TOO_FAST_OR_SILENT") ||
                (error.getCause() != null &&
                 String.valueOf(error.getCause().getMessage()).contains("NEURO_SPEECH_TOO_FAST_OR_SILENT")));
    }


    @Test
    @SuppressWarnings({"unchecked","rawtypes"})
    void resumesThirtyFirstOctoberFailedSceneFromTwoPaidVoiceIdsWithOnlyTwoNewPaidPosts() throws Exception {
        String id="f3c3190a-8f68-4a38-95df-26dc3fe17f0e";
        String owner="owner-1", room="hat-50698835685110f4";
        String assignment="Повернуться к веб-камере и смотреть прямо в камеру. "+
                "Выразительно произнести «А вы всё ещё кипятите?», выдержать паузу, "+
                "затем выразительно произнести «Тогда мы идём к вам!», "+
                "показать большой палец прямо в камеру. "+
                "Затем угрожающе погрозить кулаком прямо в камеру, встать со стула и уйти из кадра.";
        byte[] camera=generatedMedia(".webm",List.of("-f","lavfi","-i",
                "testsrc2=size=320x180:rate=15","-t","10","-an","-c:v","libvpx",
                "-deadline","realtime","-b:v","160k"));
        byte[] sample=generatedMedia(".wav",List.of("-f","lavfi","-i",
                "sine=frequency=450:sample_rate=16000","-t","10","-ac","1","-c:a","pcm_s16le"));
        byte[] paidVoice=generatedMedia(".wav",List.of("-f","lavfi","-i",
                "sine=frequency=460:sample_rate=24000:duration=7",
                "-af","apad=pad_dur=5","-t","12","-ac","1","-c:a","pcm_s16le"));
        byte[] directed=generatedMedia(".mp4",List.of("-f","lavfi","-i",
                "color=c=red:size=320x180:rate=24","-t","9","-an",
                "-c:v","libx264","-preset","ultrafast","-pix_fmt","yuv420p"));
        byte[] finalMp4=generatedMedia(".mp4",List.of("-f","lavfi","-i",
                "color=c=blue:size=320x180:rate=24","-f","lavfi","-i",
                "sine=frequency=500:sample_rate=16000","-t","9",
                "-c:v","libx264","-preset","ultrafast","-pix_fmt","yuv420p",
                "-c:a","aac","-shortest"));
        List<String> paid=new ArrayList<>();
        List<String> inspected=new ArrayList<>();
        HttpClient client=mock(HttpClient.class);
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class)))
                .thenAnswer(call -> {
                    HttpRequest req=call.getArgument(0);
                    String path=req.uri().getPath();
                    if ("POST".equals(req.method())) {
                        String model=path.substring(path.lastIndexOf('/')+1);
                        assertTrue(model.equals("kling-video-o3") || model.equals("lipsync"),
                                "Never re-pay Gemini TTS or Chatterbox");
                        paid.add(model);
                        return response("{\"request_id\":\"sim-"+model+"\"}");
                    }
                    if (path.startsWith("/api/v1/request/get/")) {
                        String requestId=path.substring(path.lastIndexOf('/')+1);
                        inspected.add(requestId);
                        String extension=requestId.equals("57010840")?"wav":
                                requestId.equals("57010833")?"mp3":"mp4";
                        return response("{\"status\":\"success\",\"result\":[\"https://gen-api.storage.yandexcloud.net/"
                                +requestId+"."+extension+"\"]}");
                    }
                    if ("gen-api.storage.yandexcloud.net".equals(req.uri().getHost())) {
                        if(path.endsWith("57010840.wav"))return response(paidVoice);
                        if(path.endsWith("sim-kling-video-o3.mp4"))return response(directed);
                        if(path.endsWith("sim-lipsync.mp4"))return response(finalMp4);
                    }
                    throw new IOException("Unexpected provider call "+req.method()+" "+req.uri());
                });
        pilot=new NeuroReplacementPilotService(new ObjectMapper(),client);
        ReflectionTestUtils.setField(pilot,"referencePublicOrigin","https://hot-hat-back.up.railway.app");
        ReflectionTestUtils.setField(pilot,"enabled",true);
        ReflectionTestUtils.setField(pilot,"key","simulated-key");

        JdbcTemplate db=mock(JdbcTemplate.class);
        ResultSet row=mock(ResultSet.class);
        when(row.getString(1)).thenReturn(id);
        when(row.getString(2)).thenReturn(room);
        when(row.getString(3)).thenReturn(owner);
        when(row.getString(4)).thenReturn("failed");
        when(row.getString(5)).thenReturn("Ошибка генерации");
        when(row.getString(6)).thenReturn("{\"tts\":\"57010833\",\"chatterbox-speech\":\"57010840\"}");
        when(row.getString(8)).thenReturn("NEURO_SPEECH_TOO_FAST_OR_SILENT voiced=5.2s lastSpeech=6.9s");
        when(row.getString(10)).thenReturn("old-camera");
        when(row.getString(11)).thenReturn("old-voice");
        when(row.getString(12)).thenReturn("video/webm");
        when(row.getString(13)).thenReturn("audio/wav");
        when(row.getString(16)).thenReturn(assignment);
        when(db.query(anyString(),any(RowMapper.class),eq(id)))
                .thenAnswer(call -> List.of(((RowMapper)call.getArgument(1)).mapRow(row,0)));
        when(db.update(anyString(),eq(id),eq(owner))).thenReturn(1,0);
        pilot.setJdbcTemplate(db);
        ObjectStore archive=mock(ObjectStore.class);
        when(archive.configured()).thenReturn(true);
        when(archive.get("old-camera")).thenReturn(camera);
        when(archive.get("old-voice")).thenReturn(sample);
        when(archive.presignGet(anyString(),any(java.time.Duration.class),eq("audio/wav"),
                isNull(),anyString())).thenReturn("https://signed.example.test/valid-audio.wav");
        when(archive.presignGet(anyString(),any(java.time.Duration.class),eq("video/mp4"),
                isNull(),anyString())).thenReturn("https://signed.example.test/valid-video.mp4");
        pilot.setObjectStore(archive);
        Map<String,Object> resumed=pilot.resumeAfterSpeechGate(owner,id,true);
        assertEquals("processing",resumed.get("status"));
        ResponseStatusException again=assertThrows(ResponseStatusException.class,
                () -> pilot.resumeAfterSpeechGate(owner,id,true));
        assertEquals(HttpStatus.CONFLICT,again.getStatusCode());
        Map<String,Object> rooms=(Map<String,Object>)ReflectionTestUtils.getField(pilot,"byRoom");
        Object job=rooms.get(room);
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);
        while(System.nanoTime()<until &&
                "processing".equals(ReflectionTestUtils.getField(job,"state")))Thread.sleep(100);
        assertEquals("ready",ReflectionTestUtils.getField(job,"state"),
                String.valueOf(ReflectionTestUtils.getField(job,"error")));
        assertEquals(List.of("kling-video-o3","lipsync"),paid,
                "Two original audio IDs must be reused with GET only");
        assertEquals(List.of("57010833","57010840","sim-kling-video-o3","sim-lipsync"),inspected);
        verify(archive).put(eq("neuro-replacements/"+owner+"/"+id+".mp4"),
                aryEq(finalMp4),eq("video/mp4"));
    }

    @Test
    @SuppressWarnings({"unchecked","rawtypes"})
    void actualHttp422UsesDocumentedGenApiErrorsValidationField() throws Exception {
        HttpClient provider=mock(HttpClient.class);
        when(provider.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class)))
                .thenAnswer(call -> {
                    HttpResponse reply=mock(HttpResponse.class);
                    when(reply.statusCode()).thenReturn(422);
                    doReturn("""
                        {"error":true,"errors_validation":{
                            "model":["Unsupported mode for video reference"],
                            "video_url":["Not accessible at https://signed.invalid/?signature=SECRET"]
                        }}
                        """).when(reply).body();
                    return reply;
                });
        pilot=new NeuroReplacementPilotService(new ObjectMapper(),provider);
        HttpRequest request=HttpRequest.newBuilder(
                java.net.URI.create("https://api.gen-api.ru/api/v1/networks/kling-video-o3"))
                .GET().build();
        Exception failed=assertThrows(Exception.class,() ->
                ReflectionTestUtils.invokeMethod(pilot,"send",request));
        StringBuilder trace=new StringBuilder();
        Throwable error=failed;
        for(int depth=0;error!=null && depth<5;depth++,error=error.getCause()){
            trace.append(' ').append(error.getMessage());
        }
        String reason=trace.toString();
        assertTrue(reason.contains("GENAPI_HTTP_422"),reason);
        assertTrue(reason.contains("model: Unsupported mode"),reason);
        assertTrue(reason.contains("video_url: Not accessible"),reason);
        assertFalse(reason.contains("signed.invalid"),reason);
        assertFalse(reason.contains("SECRET"),reason);
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
