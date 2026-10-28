package ru.hothat.testbot.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
