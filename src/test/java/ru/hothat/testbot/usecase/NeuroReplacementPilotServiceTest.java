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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;

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
    void lipSyncUsesGenApiAcceptedModeForAlreadyAlignedFullLengthAudio() {
        assertEquals("cut_off", ReflectionTestUtils.getField(
                NeuroReplacementPilotService.class, "LIPSYNC_SYNC_MODE"),
                "GenAPI rejected silence with HTTP 422; the local FFmpeg output is padded to exactly the video duration");
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
    void ownerHistoryIncludesEveryDynamicAssignmentAndExactSpokenText() throws Exception {
        String owner = "owner-1";
        JdbcTemplate db = mock(JdbcTemplate.class);
        pilot.setJdbcTemplate(db);
        ResultSet saved = mock(ResultSet.class);
        String script = "Смотрит в камеру и говорит Привет всем друзьям и хорошего дня!";
        when(saved.getString(1)).thenReturn("neuro-script-01");
        when(saved.getString(2)).thenReturn("hat-a1ece4688a6e8389");
        when(saved.getString(3)).thenReturn(owner);
        when(saved.getString(4)).thenReturn("ready");
        when(saved.getString(5)).thenReturn("Готово");
        when(saved.getString(6)).thenReturn("{\"lipsync\":\"already-paid\"}");
        when(saved.getString(9)).thenReturn("neuro-replacements/owner-1/neuro-script-01.mp4");
        when(saved.getString(11)).thenReturn(script);
        when(saved.getTimestamp(10)).thenReturn(Timestamp.from(Instant.parse("2026-10-30T13:16:56Z")));
        when(db.query(anyString(), any(RowMapper.class), eq(owner)))
                .thenAnswer(call -> List.of(((RowMapper)call.getArgument(1)).mapRow(saved,0)));

        Map<String,Object> row = pilot.history(owner).get(0);
        assertEquals("custom",row.get("sceneType"));
        assertEquals(script,row.get("sceneAssignment"));
        assertEquals("Привет всем друзьям и хорошего дня!",row.get("spokenText"));
        assertEquals(true,row.get("backgroundLocked"));
        assertEquals(true,row.get("archived"));
        assertTrue((Integer)row.get("durationSeconds")<=10);
        assertEquals("ready",row.get("status"));
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
        assertEquals("image-to-video", dynamicFields.get("model"));
        assertEquals("15", legacyFields.get("duration"), "Old paid jobs must not be rewritten");
    }

    @Test
    void invalidLipSyncMode422CannotTriggerAnyPaidReplayWithoutOwnerConsent() {
        ResponseStatusException refused=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeAfterLipSyncMode422("owner-1",
                        "f8011911-f38d-4706-92cb-546049d42d0d",false));
        assertEquals(HttpStatus.BAD_REQUEST,refused.getStatusCode());
    }

    @Test
    @SuppressWarnings({"unchecked","rawtypes"})
    void invalidLipSyncMode422FailsClosedWhenPaidKlingQualityCannotBeVerified() throws Exception {
        ReflectionTestUtils.setField(pilot,"enabled",true);
        ReflectionTestUtils.setField(pilot,"key","simulation-only");
        var archive=mock(ru.hothat.common.storage.ObjectStore.class);
        when(archive.configured()).thenReturn(true);
        pilot.setObjectStore(archive);
        JdbcTemplate db=mock(JdbcTemplate.class);
        pilot.setJdbcTemplate(db);
        ResultSet row=mock(ResultSet.class);
        String id="f8011911-f38d-4706-92cb-546049d42d0d",owner="owner-1";
        when(row.getString(1)).thenReturn(id);
        when(row.getString(2)).thenReturn("hat-fac7ed44e374eef2");
        when(row.getString(3)).thenReturn(owner);
        when(row.getString(4)).thenReturn("failed");
        when(row.getString(6)).thenReturn("{\"tts\":\"57019817\",\"chatterbox-speech\":\"57019832\",\"kling-video-o3\":\"57020589\"}");
        when(row.getString(8)).thenReturn("GENAPI_HTTP_422 sync_mode: Значение поля sync mode некорректно.");
        when(row.getString(10)).thenReturn("existing-camera");
        when(row.getString(11)).thenReturn("existing-voice");
        when(row.getString(12)).thenReturn("video/webm");
        when(row.getString(13)).thenReturn("audio/webm");
        when(row.getString(15)).thenReturn("lipsync");
        when(row.getString(16)).thenReturn(
                "Повернуться к веб-камере и смотреть прямо в камеру. "+
                "Выразительно произнести «А вы всё ещё кипятите?», выдержать паузу, "+
                "затем выразительно произнести «Тогда мы идём к вам!», "+
                "показать большой палец прямо в камеру. "+
                "Затем угрожающе погрозить кулаком прямо в камеру, встать со стула и уйти из кадра.");
        when(db.query(anyString(),any(RowMapper.class),eq(id))).thenAnswer(call->
                List.of(((RowMapper)call.getArgument(1)).mapRow(row,0)));
        when(db.update(anyString(),eq(id),eq(owner))).thenReturn(0);
        ResponseStatusException doubleClick=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeAfterLipSyncMode422(owner,id,true));
        assertEquals(HttpStatus.BAD_GATEWAY,doubleClick.getStatusCode(),
                "Never charge Lip Sync when the old paid Kling cannot be quality-checked");
        verify(db,never()).update(anyString(),eq(id),eq(owner));
    }

    @Test
    void general422OrOtherJobsCannotUseLipSyncSpecificRecovery() throws Exception {
        ReflectionTestUtils.setField(pilot,"enabled",true);
        ReflectionTestUtils.setField(pilot,"key","simulation-only");
        var archive=mock(ru.hothat.common.storage.ObjectStore.class);
        when(archive.configured()).thenReturn(true);
        pilot.setObjectStore(archive);
        JdbcTemplate db=mock(JdbcTemplate.class);
        pilot.setJdbcTemplate(db);
        ResultSet row=mock(ResultSet.class);
        when(row.getString(1)).thenReturn("job-with-unknown-422");
        when(row.getString(2)).thenReturn("hat-fac7ed44e374eef2");
        when(row.getString(3)).thenReturn("owner-1");
        when(row.getString(4)).thenReturn("failed");
        when(row.getString(6)).thenReturn("{\"tts\":\"57019817\",\"chatterbox-speech\":\"57019832\",\"kling-video-o3\":\"57020589\"}");
        when(row.getString(8)).thenReturn("GENAPI_HTTP_422 video_url: invalid URL");
        when(row.getString(15)).thenReturn("lipsync");
        when(db.query(anyString(),any(RowMapper.class),eq("job-with-unknown-422")))
                .thenAnswer(call->List.of(((RowMapper)call.getArgument(1)).mapRow(row,0)));
        ResponseStatusException rejected=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeAfterLipSyncMode422("owner-1","job-with-unknown-422",true));
        assertEquals(HttpStatus.CONFLICT,rejected.getStatusCode());
        verify(db,never()).update(anyString(),anyString(),anyString());
    }

    @Test
    void speechGateResumeRequiresExplicitConsentBeforeReadingOrCharging() {
        ResponseStatusException e=assertThrows(ResponseStatusException.class,
                () -> pilot.resumeAfterSpeechGate("owner-1","old-paid-job",false));
        assertEquals(HttpStatus.BAD_REQUEST,e.getStatusCode());
    }

    @Test
    @SuppressWarnings({"unchecked","rawtypes"})
    void speechGateResumeWithOldIdsRejectsDuplicateDatabaseClaimBeforeAnyPaidPost() throws Exception {
        ReflectionTestUtils.setField(pilot,"enabled",true);
        ReflectionTestUtils.setField(pilot,"key","simulation-only");
        ru.hothat.common.storage.ObjectStore archive=mock(ru.hothat.common.storage.ObjectStore.class);
        when(archive.configured()).thenReturn(true);
        pilot.setObjectStore(archive);
        JdbcTemplate db=mock(JdbcTemplate.class);
        pilot.setJdbcTemplate(db);
        ResultSet row=mock(ResultSet.class);
        String id="f3c3190a-8f68-4a38-95df-26dc3fe17f0e", owner="owner-1";
        when(row.getString(1)).thenReturn(id);
        when(row.getString(2)).thenReturn("hat-50698835685110f4");
        when(row.getString(3)).thenReturn(owner);
        when(row.getString(4)).thenReturn("failed");
        when(row.getString(5)).thenReturn("Ошибка генерации");
        when(row.getString(6)).thenReturn("{\"tts\":\"57010833\",\"chatterbox-speech\":\"57010840\"}");
        when(row.getString(8)).thenReturn(
                "NEURO_SPEECH_TOO_FAST_OR_SILENT voiced=5.2s lastSpeech=6.9s");
        when(row.getString(10)).thenReturn("camera-already-in-S3");
        when(row.getString(11)).thenReturn("voice-already-in-S3");
        when(row.getString(12)).thenReturn("video/webm");
        when(row.getString(13)).thenReturn("audio/webm");
        when(row.getString(16)).thenReturn(
                "Повернуться к веб-камере и смотреть прямо в камеру. "+
                "Выразительно произнести «А вы всё ещё кипятите?», выдержать паузу, "+
                "затем выразительно произнести «Тогда мы идём к вам!», "+
                "показать большой палец прямо в камеру. "+
                "Затем угрожающе погрозить кулаком прямо в камеру, встать со стула и уйти из кадра.");
        when(db.query(anyString(),any(RowMapper.class),eq(id)))
                .thenAnswer(call -> List.of(((RowMapper)call.getArgument(1)).mapRow(row,0)));
        // A competing browser/device already claimed this job. Absolutely no
        // second paid POST may be sent, even with explicit consent.
        when(db.update(anyString(),eq(id),eq(owner))).thenReturn(0);
        ResponseStatusException duplicate=assertThrows(ResponseStatusException.class,
                () -> pilot.resumeAfterSpeechGate(owner,id,true));
        assertEquals(HttpStatus.CONFLICT,duplicate.getStatusCode());
        assertTrue(String.valueOf(duplicate.getReason()).contains("ALREADY_CLAIMED"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void klingJsonReferenceUsesSignedUrlAndRealBooleanFields() {
        String assignment="Говорит Привет всем друзьям, затем показывает большой палец!";
        NeuroScenePlan plan=NeuroScenePlan.plan(assignment);
        Map<String,Object> fields=ReflectionTestUtils.invokeMethod(
                pilot,"klingJsonReferenceFields",plan,
                "https://signed.example.test/input.jpg");
        assertEquals("https://signed.example.test/input.jpg",fields.get("start_image_url"));
        assertEquals("image-to-video",fields.get("model"));
        assertEquals(true,fields.get("pro"));
        assertFalse(fields.containsKey("generate_audio"),"Documented default is false");
        assertFalse(fields.containsKey("keep_audio"),"Documented default is false");
        assertFalse(fields.containsKey("aspect_ratio"),"Documented default is 16:9");
        assertEquals(String.valueOf(plan.durationSeconds()),fields.get("duration"));
        assertFalse(fields.containsKey("video_url"),"Video reference copies motions");
        assertTrue(String.valueOf(fields.get("prompt")).contains("@Image1"));
    }

    @Test
    void nestedGenApiValidationErrorIsNotLostOn422() throws Exception {
        var data = new ObjectMapper().readTree(
                "{\"detail\":[{\"loc\":[\"body\",\"video_url\"],\"type\":\"missing\","+
                "\"msg\":\"Field required\"}]}");
        String reason=ReflectionTestUtils.invokeMethod(pilot,"validationDetails",data);
        assertTrue(reason.contains("Field required"));
        assertTrue(reason.contains("video_url"));
    }

    @Test
    void documentedGenApi422ErrorsValidationRevealsRejectedFieldsWithoutSignedUrls() throws Exception {
        var data = new ObjectMapper().readTree("""
                {"error":true,"errors_validation":{
                  "model":["Unknown generation mode"],
                  "video_url":["Cannot download https://private.example.test/?Signature=SECRET123"],
                  "duration":["Must be between 3 and 15 seconds"]
                }}
                """);
        String diagnostic=ReflectionTestUtils.invokeMethod(pilot,"validationDetails",data);
        assertTrue(diagnostic.contains("model: Unknown generation mode"),diagnostic);
        assertTrue(diagnostic.contains("video_url: Cannot download"),diagnostic);
        assertTrue(diagnostic.contains("duration: Must be between 3 and 15"),diagnostic);
        assertFalse(diagnostic.contains("private.example.test"),diagnostic);
        assertFalse(diagnostic.contains("SECRET123"),diagnostic);
    }

    @Test
    void documentedGenApi422NestedErrorsRemainActionable() throws Exception {
        var data = new ObjectMapper().readTree("""
                {"error":true,"errors_validation":{"body":{
                  "model":{"errors":[{"loc":["body","model"],"msg":"Unsupported generation mode"}]},
                  "video_url":["Invalid input format"]
                }}}
                """);
        String diagnostic=ReflectionTestUtils.invokeMethod(pilot,"validationDetails",data);
        assertTrue(diagnostic.contains("model: Unsupported generation mode"),diagnostic);
        assertTrue(diagnostic.contains("video_url: Invalid input format"),diagnostic);
    }

    @Test
    void documentedGenApi422EmptyValidationMapDoesNotPretendFieldIsKnown() throws Exception {
        var data = new ObjectMapper().readTree("""
                {"error":true,"errors_validation":{}}
                """);
        String diagnostic=ReflectionTestUtils.invokeMethod(pilot,"validationDetails",data);
        assertTrue(diagnostic.contains("without field descriptions"),diagnostic);
    }

    @Test
    void definitiveKling422RequiresNewConsentAndRefusesStaleClaim() throws Exception {
        ResponseStatusException declined=assertThrows(ResponseStatusException.class,
                () -> pilot.resumeAfterKlingValidation("owner-1","old-422",false));
        assertEquals(HttpStatus.BAD_REQUEST,declined.getStatusCode());

        ReflectionTestUtils.setField(pilot,"enabled",true);
        ReflectionTestUtils.setField(pilot,"key","simulation");
        var archive=mock(ru.hothat.common.storage.ObjectStore.class);
        when(archive.configured()).thenReturn(true);
        pilot.setObjectStore(archive);
        JdbcTemplate db=mock(JdbcTemplate.class);
        pilot.setJdbcTemplate(db);
        ResultSet saved=mock(ResultSet.class);
        when(saved.getString(1)).thenReturn("old-422");
        when(saved.getString(2)).thenReturn("hat-50698835685110f4");
        when(saved.getString(3)).thenReturn("owner-1");
        when(saved.getString(4)).thenReturn("failed");
        when(saved.getString(5)).thenReturn("Ошибка генерации");
        when(saved.getString(6)).thenReturn("{\"tts\":\"57010833\",\"chatterbox-speech\":\"57010840\"}");
        when(saved.getString(8)).thenReturn("GENAPI_HTTP_422 provider rejected request");
        when(saved.getString(10)).thenReturn("camera-key");
        when(saved.getString(11)).thenReturn("voice-key");
        when(saved.getString(12)).thenReturn("video/webm");
        when(saved.getString(13)).thenReturn("audio/wav");
        when(saved.getString(15)).thenReturn("kling-video-o3");
        when(saved.getString(16)).thenReturn(
                "Говорит Привет всем друзьям и показывает большой палец.");
        when(db.query(anyString(),any(RowMapper.class),eq("old-422")))
                .thenAnswer(call -> List.of(((RowMapper)call.getArgument(1)).mapRow(saved,0)));
        when(db.update(anyString(),eq("old-422"),eq("owner-1"))).thenReturn(0);
        ResponseStatusException duplicate=assertThrows(ResponseStatusException.class,
                () -> pilot.resumeAfterKlingValidation("owner-1","old-422",true));
        assertEquals(HttpStatus.CONFLICT,duplicate.getStatusCode());
        assertTrue(String.valueOf(duplicate.getReason()).contains("ALREADY_CLAIMED"));
    }

    @Test
    @SuppressWarnings({"unchecked","rawtypes"})
    void freeVideoReferencePreflightNeverOrdersGenApiWhenArchiveIsMissing() throws Exception {
        JdbcTemplate db=mock(JdbcTemplate.class);
        pilot.setJdbcTemplate(db);
        ResultSet rs=mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("reference-test");
        when(rs.getString(2)).thenReturn("hat-50698835685110f4");
        when(rs.getString(3)).thenReturn("owner-1");
        when(rs.getString(4)).thenReturn("failed");
        when(rs.getString(6)).thenReturn("{}");
        when(rs.getString(16)).thenReturn("Говорит Привет всем друзьям и уходит.");
        when(db.query(anyString(),any(RowMapper.class),eq("reference-test")))
                .thenAnswer(call -> List.of(((RowMapper)call.getArgument(1)).mapRow(rs,0)));
        var outcome=pilot.inspectKlingReference("owner-1","reference-test");
        assertEquals("MISSING_ARCHIVED_REFERENCE",outcome.get("check"));
        assertEquals(0,outcome.get("paidCalls"));
        assertEquals(false,outcome.get("safeToRetry"));
        ResponseStatusException denied=assertThrows(ResponseStatusException.class,
                () -> pilot.inspectKlingReference("other-owner","reference-test"));
        assertEquals(HttpStatus.NOT_FOUND,denied.getStatusCode());
    }

    @Test
    void finalKlingTestRequiresConsentAndCannotRunOnAnotherJob() {
        ResponseStatusException noConsent=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeVerifiedKlingFinal("owner-1",
                        "f3c3190a-8f68-4a38-95df-26dc3fe17f0e",false));
        assertEquals(HttpStatus.BAD_REQUEST,noConsent.getStatusCode());
        ReflectionTestUtils.setField(pilot,"enabled",true);
        ReflectionTestUtils.setField(pilot,"key","test-only");
        var archive=mock(ru.hothat.common.storage.ObjectStore.class);
        when(archive.configured()).thenReturn(true);
        pilot.setObjectStore(archive);
        pilot.setJdbcTemplate(mock(JdbcTemplate.class));
        ResponseStatusException other=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeVerifiedKlingFinal("owner-1","not-the-original-paid-job",true));
        assertEquals(HttpStatus.CONFLICT,other.getStatusCode());
    }

    @Test
    void minimalKlingRetryRequiresFreshConsentAndIsLimitedToOriginalJob() {
        ResponseStatusException missing=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeMinimalKlingOnce("owner-1",
                        "f3c3190a-8f68-4a38-95df-26dc3fe17f0e",false));
        assertEquals(HttpStatus.BAD_REQUEST,missing.getStatusCode());
        ReflectionTestUtils.setField(pilot,"enabled",true);
        ReflectionTestUtils.setField(pilot,"key","test-only");
        var archive=mock(ru.hothat.common.storage.ObjectStore.class);
        when(archive.configured()).thenReturn(true);
        pilot.setObjectStore(archive);
        pilot.setJdbcTemplate(mock(JdbcTemplate.class));
        ResponseStatusException other=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeMinimalKlingOnce("owner-1","different-paid-job",true));
        assertEquals(HttpStatus.CONFLICT,other.getStatusCode());
    }

    @Test
    @SuppressWarnings({"unchecked","rawtypes"})
    void minimalKlingRetryCannotChargeWhenAnotherTabHasAlreadyClaimed() throws Exception {
        ReflectionTestUtils.setField(pilot,"enabled",true);
        ReflectionTestUtils.setField(pilot,"key","test-only");
        var archive=mock(ru.hothat.common.storage.ObjectStore.class);
        when(archive.configured()).thenReturn(true);
        pilot.setObjectStore(archive);
        JdbcTemplate db=mock(JdbcTemplate.class);
        pilot.setJdbcTemplate(db);
        ResultSet saved=mock(ResultSet.class);
        String jobId="f3c3190a-8f68-4a38-95df-26dc3fe17f0e";
        when(saved.getString(1)).thenReturn(jobId);
        when(saved.getString(2)).thenReturn("hat-50698835685110f4");
        when(saved.getString(3)).thenReturn("owner-1");
        when(saved.getString(4)).thenReturn("failed");
        when(saved.getString(5)).thenReturn("Ошибка генерации");
        when(saved.getString(6)).thenReturn("{\"tts\":\"57010833\",\"chatterbox-speech\":\"57010840\"}");
        when(saved.getString(8)).thenReturn("GENAPI_HTTP_422 model: invalid mode");
        when(saved.getString(10)).thenReturn("camera");
        when(saved.getString(11)).thenReturn("voice");
        when(saved.getString(12)).thenReturn("video/webm");
        when(saved.getString(13)).thenReturn("audio/webm");
        when(saved.getString(15)).thenReturn("kling-video-o3");
        when(saved.getString(16)).thenReturn("Говорит Привет всем друзьям и уходит.");
        when(saved.getInt(17)).thenReturn(3);
        when(db.query(anyString(),any(RowMapper.class),eq(jobId)))
                .thenAnswer(call -> List.of(((RowMapper)call.getArgument(1)).mapRow(saved,0)));
        // A second browser won the atomic claim; the first must NOT start
        // another provider task. Preflight can also refuse before CAS.
        when(db.update(anyString(),eq(jobId),eq("owner-1"))).thenReturn(0);
        // Simulate the already-passing, free S3/MP4 preflight, then force
        // the DB CAS to lose. No paid provider POST must be reachable.
        NeuroReplacementPilotService subject=org.mockito.Mockito.spy(pilot);
        org.mockito.Mockito.doReturn(Map.of("check","REFERENCE_READY_PROVIDER_SCHEMA_NOT_PROVEN"))
                .when(subject).inspectKlingReference("owner-1",jobId);
        var result=assertThrows(ResponseStatusException.class,
                ()->subject.resumeMinimalKlingOnce("owner-1",jobId,true));
        assertEquals(HttpStatus.CONFLICT,result.getStatusCode());
        assertTrue(String.valueOf(result.getReason()).contains("ALREADY_CLAIMED"));
        org.mockito.Mockito.verify(db).update(anyString(),eq(jobId),eq("owner-1"));
    }

    @Test
    void studioRejectsInvalidIdAndRequiresNewPaymentConsent() {
        var sample = new MockMultipartFile("video","camera.webm","video/webm",new byte[]{1});
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,
                ()->pilot.startStudio("owner-1","bad-uuid",true,sample,sample,
                        "Говорит Привет всем друзьям.")).getStatusCode());
        ReflectionTestUtils.setField(pilot,"enabled",true);
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,
                ()->pilot.startStudio("owner-1","aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                        false,sample,sample,"Говорит Привет всем друзьям.")).getStatusCode());
    }

    @Test
    void sourceReferenceHasCleanMp4SuffixWithoutAnySignedS3Query() throws Exception {
        var db=mock(JdbcTemplate.class);
        var store=mock(ru.hothat.common.storage.ObjectStore.class);
        when(store.configured()).thenReturn(true);
        pilot.setJdbcTemplate(db);
        pilot.setObjectStore(store);
        ReflectionTestUtils.setField(pilot,"referencePublicOrigin",
                "https://hot-hat-back.up.railway.app");
        Class<?> jobType=Class.forName(
                "ru.hothat.testbot.usecase.NeuroReplacementPilotService$Job");
        var ctor=jobType.getDeclaredConstructor(String.class,String.class,String.class);
        ctor.setAccessible(true);
        Object job=ctor.newInstance("test-123","hat-aabbccddeeff0011","owner-1");
        String clean=ReflectionTestUtils.invokeMethod(pilot,
                "publishSceneCameraReference",job,new byte[300]);
        assertTrue(clean.matches(
                "https://hot-hat-back[.]up[.]railway[.]app/api/v2/public/neuro-references/[a-f0-9]{64}/input[.]mp4"),
                clean);
        assertFalse(clean.contains("?"),"S3 query params caused the real GenAPI 422");
        assertFalse(clean.contains("X-Amz"),"Never forward S3 signatures to GenAPI");
        org.mockito.Mockito.verify(store).put(
                eq("neuro-replacements/owner-1/test-123-kling-reference.mp4"),
                any(byte[].class),eq("video/mp4"));
    }

    @Test
    void newImageToVideoUsesOnlyOnePrivateCameraFrameWithShortLivedCleanJpegUrl() throws Exception {
        JdbcTemplate db=mock(JdbcTemplate.class);
        var store=mock(ru.hothat.common.storage.ObjectStore.class);
        when(store.configured()).thenReturn(true);
        pilot.setJdbcTemplate(db);
        pilot.setObjectStore(store);
        ReflectionTestUtils.setField(pilot,"referencePublicOrigin",
                "https://hot-hat-back.up.railway.app");
        Class<?> jobType=Class.forName(NeuroReplacementPilotService.class.getName()+"$Job");
        var ctor=jobType.getDeclaredConstructor(String.class,String.class,String.class);
        ctor.setAccessible(true);
        Object job=ctor.newInstance("test-jpg","hat-aabbccddeeff0011","owner-1");
        String url=ReflectionTestUtils.invokeMethod(pilot,"cleanGenApiInputUrl",job,
                "neuro-replacements/owner-1/test-jpg-kling-still.jpg","jpg");
        assertTrue(url.matches("https://hot-hat-back[.]up[.]railway[.]app/"+
                "api/v2/public/neuro-references/[a-f0-9]{64}/input[.]jpg"),url);
        assertFalse(url.contains("?"));
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(ResponseStatusException.class,
                ()->pilot.publicKlingStill("not-a-token")).getStatusCode());
    }

    @Test
    void reviewedActingCannotSpendWithoutExplicitConsent() {
        ResponseStatusException denied=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeReviewedActing("owner-1","some-job",false));
        assertEquals(HttpStatus.BAD_REQUEST,denied.getStatusCode());
    }

    @Test
    void lipSyncInputAlsoUsesCleanWavUrlWithoutS3Signature() throws Exception {
        JdbcTemplate db=mock(JdbcTemplate.class);
        var store=mock(ru.hothat.common.storage.ObjectStore.class);
        when(store.configured()).thenReturn(true);
        pilot.setJdbcTemplate(db);
        pilot.setObjectStore(store);
        ReflectionTestUtils.setField(pilot,"referencePublicOrigin",
                "https://hot-hat-back.up.railway.app");
        Class<?> jobType=Class.forName(
                "ru.hothat.testbot.usecase.NeuroReplacementPilotService$Job");
        var ctor=jobType.getDeclaredConstructor(String.class,String.class,String.class);
        ctor.setAccessible(true);
        Object job=ctor.newInstance("test-abc","hat-aabbccddeeff0011","owner-1");
        String clean=ReflectionTestUtils.invokeMethod(pilot,"cleanGenApiInputUrl",
                job,"neuro-replacements/owner-1/test-abc-lipsync-voice.wav","wav");
        assertTrue(clean.matches(
                "https://hot-hat-back[.]up[.]railway[.]app/api/v2/public/neuro-references/[a-f0-9]{64}/input[.]wav"),clean);
        assertFalse(clean.contains("?"),"signed S3 query causes provider file validation errors");
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(ResponseStatusException.class,
                ()->pilot.publicVoiceReference("not-a-token")).getStatusCode());
    }

}
