package ru.hothat.testbot.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

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
        assertTrue(prompt.contains("thumbs-up"));
        assertTrue(prompt.contains("one continuous static webcam shot"));
    }

    @Test
    void klingO3EditVideoNeverSendsUnsupportedDuration() {
        Map<?, ?> fields = ReflectionTestUtils.invokeMethod(pilot, "klingEditFields");
        assertEquals("edit-video", fields.get("model"));
        assertEquals("true", fields.get("pro"));
        assertEquals("false", fields.get("keep_audio"));
        assertFalse(fields.containsKey("duration"),
                "Kling edit-video must inherit duration from its reference video");
        assertFalse(fields.containsKey("generate_audio"),
                "Video edit should not request separate generated sound");
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
}
