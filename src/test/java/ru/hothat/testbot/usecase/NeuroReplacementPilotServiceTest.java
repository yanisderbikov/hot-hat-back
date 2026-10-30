package ru.hothat.testbot.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.List;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeuroReplacementPilotServiceTest {
    private final NeuroReplacementPilotService pilot = new NeuroReplacementPilotService(new ObjectMapper());

    @AfterEach
    void close() {
        pilot.close();
    }

    @Test
    void paidRequestRequiresExplicitConsent() {
        ReflectionTestUtils.setField(pilot, "enabled", true);
        ReflectionTestUtils.setField(pilot, "key", "local-test-token");
        MockMultipartFile video = new MockMultipartFile("video", "camera.webm", "video/webm", new byte[]{1});
        MockMultipartFile voice = new MockMultipartFile("voice", "voice.webm", "audio/webm", new byte[]{1});

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> pilot.start("hat-0123456789abcdef", "owner-1", false, video, voice));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsUnsupportedMediaBeforeAnyPaidJobIsCreated() {
        ReflectionTestUtils.setField(pilot, "enabled", true);
        ReflectionTestUtils.setField(pilot, "key", "local-test-token");
        MockMultipartFile video = new MockMultipartFile("video", "evil.exe", "application/octet-stream", new byte[]{1});
        MockMultipartFile voice = new MockMultipartFile("voice", "voice.webm", "audio/webm", new byte[]{1});

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> pilot.start("hat-0123456789abcdef", "owner-1", true, video, voice));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void featureCanBeDisabledWithoutUnsettingGenApiCredentials() {
        ReflectionTestUtils.setField(pilot, "enabled", false);
        ReflectionTestUtils.setField(pilot, "key", "local-test-token");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> pilot.start("hat-0123456789abcdef", "owner-1", true, null, null));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void unknownJobsAreNotExposed() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> pilot.status("hat-0123456789abcdef", "owner-1", "not-his-job"));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void extractsNestedGenApiFailureInsteadOfGenericError() throws Exception {
        ObjectMapper json = new ObjectMapper();
        var response = json.readTree("{\"status\":\"error\",\"data\":{\"error\":{\"message\":\"Unsupported video format\"}}}");
        String message = ReflectionTestUtils.invokeMethod(pilot, "errorCode", response);
        assertEquals("Unsupported video format", message);
    }

    @Test
    void doesNotInventProviderFailureReason() throws Exception {
        ObjectMapper json = new ObjectMapper();
        var response = json.readTree("{\"status\":\"error\"}");
        String message = ReflectionTestUtils.invokeMethod(pilot, "errorCode", response);
        assertEquals("", message);
    }

    @Test
    void usesTheSameKlingVideoO3ModelAsTheSuccessfulManualTest() {
        assertEquals("kling-video-o3",
                ReflectionTestUtils.getField(NeuroReplacementPilotService.class, "VIDEO_MODEL_ID"));
        String prompt = (String) ReflectionTestUtils.getField(
                NeuroReplacementPilotService.class, "VIDEO_PROMPT");
        assertTrue(prompt.contains("THUMBS-UP"));
        assertTrue(prompt.contains("GOODBYE WAVE"));
        assertTrue(prompt.contains("STANDS UP"));
        assertTrue(prompt.contains("WALKS AWAY"));
        assertTrue(prompt.contains("APPEARANCE AND SET REFERENCE ONLY"));
        assertFalse(prompt.contains("Edit the supplied webcam video"));
    }

    @Test
    void lipSyncRetainsFullVideoWhenTransferredSpeechIsShorter() {
        assertEquals("silence", ReflectionTestUtils.getField(
                NeuroReplacementPilotService.class, "LIPSYNC_SYNC_MODE"),
                "cut_off shortens the paid result to the speech duration");
    }

    @Test
    void klingO3ReferenceVideoUsesExplicitFifteenSecondActingDuration() {
        Map<?, ?> fields = ReflectionTestUtils.invokeMethod(pilot, "klingEditFields");
        assertEquals("reference-to-video", fields.get("model"),
                "edit-video copies head grooming instead of creating ordered gestures");
        assertEquals("15", fields.get("duration"));
        assertEquals("true", fields.get("pro"));
        assertEquals("false", fields.get("keep_audio"));
        assertEquals("false", fields.get("generate_audio"));
        assertEquals("16:9", fields.get("aspect_ratio"));
        assertTrue(((String)fields.get("prompt")).length() <= 2500,
                "Kling reference prompt may not exceed provider limit");
    }

    @Test
    void voiceScriptHasOnlyTheRequestedCatchphraseAndExpressiveSourceTts() {
        String phrase=(String) ReflectionTestUtils.getField(
                NeuroReplacementPilotService.class,"SPOKEN_TEXT");
        assertTrue(phrase.contains("А вы всё ещё кипятите?"));
        assertTrue(phrase.contains("Тогда мы идём к вам!"));
        assertFalse(phrase.contains("не бойся"));
        assertEquals("gemini-tts",ReflectionTestUtils.getField(
                NeuroReplacementPilotService.class,"TTS_MODEL_ID"));
        assertTrue(((String)ReflectionTestUtils.getField(
                NeuroReplacementPilotService.class,"SPEECH_STYLE")).contains("unhurriedly"));
    }

    @Test
    void rejectsOutOfRangeObservationWindowBeforePaidWork() {
        ReflectionTestUtils.setField(pilot, "enabled", true);
        ReflectionTestUtils.setField(pilot, "key", "local-test-token");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> pilot.start("hat-0123456789abcdef", "owner-1", true, null, null, 29.5));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsNonFiniteObservationWindowBeforePaidWork() {
        ReflectionTestUtils.setField(pilot, "enabled", true);
        ReflectionTestUtils.setField(pilot, "key", "local-test-token");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> pilot.start("hat-0123456789abcdef", "owner-1", true, null, null, Double.NaN));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void refusesPaidWorkWhenDurableDatabaseOrArchiveIsUnavailable() {
        ReflectionTestUtils.setField(pilot, "enabled", true);
        ReflectionTestUtils.setField(pilot, "key", "local-test-token");
        MockMultipartFile video = new MockMultipartFile("video", "camera.webm", "video/webm", new byte[]{1, 2});
        MockMultipartFile voice = new MockMultipartFile("voice", "voice.webm", "audio/webm", new byte[]{1, 2});

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> pilot.start("hat-0123456789abcdef", "owner-1", true, video, voice));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void interruptedJobCannotBeBilledAgainInTheSameRoom() throws Exception {
        ReflectionTestUtils.setField(pilot, "enabled", true);
        ReflectionTestUtils.setField(pilot, "key", "local-test-token");
        Class<?> jobClass = Class.forName(NeuroReplacementPilotService.class.getName() + "$Job");
        var ctor = jobClass.getDeclaredConstructor(String.class, String.class, String.class);
        ctor.setAccessible(true);
        Object oldJob = ctor.newInstance("existing-hot-hat-id", "hat-0123456789abcdef", "owner-1");
        ReflectionTestUtils.setField(oldJob, "state", "interrupted");
        Map<String, Object> byRoom = (Map<String, Object>) ReflectionTestUtils.getField(pilot, "byRoom");
        byRoom.put("hat-0123456789abcdef", oldJob);

        // Even new, valid uploaded media must not authorize a second POST.
        MockMultipartFile video = new MockMultipartFile("video", "camera.webm", "video/webm", new byte[]{1, 2});
        MockMultipartFile voice = new MockMultipartFile("voice", "voice.webm", "audio/webm", new byte[]{1, 2});
        Map<String, Object> result = pilot.start("hat-0123456789abcdef", "owner-1", true, video, voice);
        assertEquals("existing-hot-hat-id", result.get("jobId"));
        assertEquals("interrupted", result.get("status"));
        assertEquals(1, byRoom.size());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void databaseCheckpointBeatsStaleCachedProcessingStateInHistoryAndStatus() throws Exception {
        String room = "hat-a1ece4688a6e8389";
        String id = "paid-job-previously-completed";
        String owner = "owner-1";
        Class<?> jobType = Class.forName(NeuroReplacementPilotService.class.getName() + "$Job");
        var ctor = jobType.getDeclaredConstructor(String.class, String.class, String.class);
        ctor.setAccessible(true);
        Object stale = ctor.newInstance(id, room, owner);
        ReflectionTestUtils.setField(stale, "state", "processing");
        ReflectionTestUtils.setField(stale, "stage", "Видео и озвучка");
        Map<String, Object> byRoom = (Map<String, Object>) ReflectionTestUtils.getField(pilot, "byRoom");
        byRoom.put(room, stale);

        JdbcTemplate db = mock(JdbcTemplate.class);
        pilot.setJdbcTemplate(db);
        ResultSet saved = mock(ResultSet.class);
        when(saved.getString(1)).thenReturn(id);
        when(saved.getString(2)).thenReturn(room);
        when(saved.getString(3)).thenReturn(owner);
        when(saved.getString(4)).thenReturn("ready");
        when(saved.getString(5)).thenReturn("Готово");
        when(saved.getString(6)).thenReturn("{\"lipsync\":\"567890\"}");
        when(saved.getString(9)).thenReturn("neuro-replacements/owner-1/result.mp4");
        when(saved.getTimestamp(10)).thenReturn(Timestamp.from(Instant.parse("2026-10-29T13:16:56Z")));

        when(db.query(anyString(), any(RowMapper.class), eq(owner)))
                .thenAnswer(call -> List.of(((RowMapper) call.getArgument(1)).mapRow(saved, 0)));
        when(db.query(anyString(), any(RowMapper.class), eq(id), eq(owner)))
                .thenAnswer(call -> List.of(((RowMapper) call.getArgument(1)).mapRow(saved, 0)));

        Map<String, Object> archived = pilot.history(owner).get(0);
        assertEquals("ready", archived.get("status"));
        assertEquals("Готово", archived.get("stage"));
        assertEquals(true, archived.get("archived"));
        Map<String, Object> direct = pilot.status(room, owner, id);
        assertEquals("ready", direct.get("status"));
        assertEquals("Готово", direct.get("stage"));
    }
    @Test
    void dynamicSceneIsPassedToKlingWithoutChangingLegacyPilotFields() {
        NeuroScenePlan scene = NeuroScenePlan.plan(
                "Этот игрок должен сказать - а вы все еще кипятите? пауза тогда мы идем к вам "
                + "и показывает большой палец, затем грозит кулаком, встаёт и уходит.");
        Map<String, String> dynamicFields = ReflectionTestUtils.invokeMethod(
                pilot, "klingEditFields", scene);
        Map<String, String> legacyFields = ReflectionTestUtils.invokeMethod(
                pilot, "klingEditFields");
        assertEquals(String.valueOf(scene.durationSeconds()), dynamicFields.get("duration"));
        assertTrue(scene.durationSeconds() >= 4 && scene.durationSeconds() <= 10);
        assertTrue(dynamicFields.get("prompt").contains("BACKGROUND LOCK"));
        assertTrue(dynamicFields.get("prompt").contains("грозит кулаком"));
        assertEquals("reference-to-video", dynamicFields.get("model"));
        assertEquals("15", legacyFields.get("duration"), "Old paid jobs must not be rewritten");
    }

}
