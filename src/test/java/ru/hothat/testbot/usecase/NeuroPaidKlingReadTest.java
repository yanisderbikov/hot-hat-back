package ru.hothat.testbot.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import ru.hothat.common.storage.ObjectStore;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SCRUM-36: no-charge reads must never be mistaken for a new paid Kling task.
 * The provider ID is recorded on submission, NOT only after a successful video.
 */
class NeuroPaidKlingReadTest {
    private static final String JOB = "25e45c7a-64a1-45ad-b080-973e9ad20009";
    private static final String OWNER = "owner-1";

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static NeuroReplacementPilotService service(HttpClient http, ObjectStore store)
            throws Exception {
        JdbcTemplate db = mock(JdbcTemplate.class);
        ResultSet row = mock(ResultSet.class);
        when(row.getString(1)).thenReturn(JOB);
        when(row.getString(2)).thenReturn("hat-fac7ed44e374eef2");
        when(row.getString(3)).thenReturn(OWNER);
        when(row.getString(4)).thenReturn("failed");
        when(row.getString(6)).thenReturn(
                "{\"kling-video-o3\":\"57020589\",\"character-mode\":\"genapi-o3-video-identity-reference\"}");
        when(db.query(anyString(), any(RowMapper.class), eq(JOB)))
                .thenAnswer(call -> List.of(((RowMapper)call.getArgument(1)).mapRow(row, 0)));
        when(store.configured()).thenReturn(true);
        NeuroReplacementPilotService pilot =
                new NeuroReplacementPilotService(new ObjectMapper(), http);
        pilot.setJdbcTemplate(db);
        pilot.setObjectStore(store);
        return pilot;
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void failedExistingProviderIdIsReportedWithoutChargingAgain() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"status\":\"error\",\"error\":\"provider failed\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        ObjectStore store = mock(ObjectStore.class);
        try (var pilot = new AutoCloseablePilot(service(http, store))) {
            ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                    () -> pilot.value.paidKlingPreview(OWNER, JOB));
            assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
            assertEquals("NEURO_PAID_KLING_PROVIDER_FAILED", failure.getReason());
            var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
            verify(http, times(1)).send(request.capture(),
                    any(HttpResponse.BodyHandler.class));
            assertEquals("GET", request.getValue().method());
            assertTrue(request.getValue().uri().getPath().endsWith("/request/get/57020589"));
            verify(store, never()).put(anyString(), any(byte[].class), anyString());
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void processingExistingProviderIdIsNotClaimedAsAvailable() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"status\":\"processing\",\"progress\":60}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        try (var pilot = new AutoCloseablePilot(service(http, mock(ObjectStore.class)))) {
            ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                    () -> pilot.value.paidKlingPreview(OWNER, JOB));
            assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
            assertEquals("NEURO_PAID_KLING_PROVIDER_NOT_READY", failure.getReason());
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void providerCannotRedirectDownloadToUntrustedOrigin() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(
                "{\"status\":\"success\",\"result\":[\"https://evil.example.invalid/secret.mp4\"]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        try (var pilot = new AutoCloseablePilot(service(http, mock(ObjectStore.class)))) {
            ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                    () -> pilot.value.paidKlingPreview(OWNER, JOB));
            assertEquals(HttpStatus.BAD_GATEWAY, failure.getStatusCode());
            assertEquals("NEURO_PAID_KLING_INVALID_RESULT_URL", failure.getReason());
            verify(http, times(1)).send(any(HttpRequest.class),
                    any(HttpResponse.BodyHandler.class));
        }
    }

    @Test
    void archivedKlingMp4IsReturnedWithoutProviderRequest() throws Exception {
        HttpClient http = mock(HttpClient.class);
        ObjectStore store = mock(ObjectStore.class);
        byte[] savedVideo = new byte[1024];
        when(store.get("neuro-replacements/" + OWNER + "/" + JOB + "-paid-kling.mp4"))
                .thenReturn(savedVideo);
        try (var pilot = new AutoCloseablePilot(service(http, store))) {
            assertArrayEquals(savedVideo, pilot.value.paidKlingPreview(OWNER, JOB));
            verifyNoInteractions(http);
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void providerStatusReadsOnlyRealGenApiIdsAndRedactsFailure() throws Exception {
        HttpClient http=mock(HttpClient.class);
        HttpResponse<String> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(
            "{\"status\":\"error\",\"progress\":100,"+
            "\"data\":{\"error\":{\"message\":\"FILE_ERROR failed to read https://private.example.com/secret.mp4?Signature=SECRET_TOKEN\"}}}");
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        try(var pilot=new AutoCloseablePilot(service(http,mock(ObjectStore.class)))) {
            var diagnosis=pilot.value.inspectProvider(OWNER,JOB);
            var jobs=(java.util.Map<String,java.util.Map<String,Object>>)diagnosis.get("providerRequests");
            assertEquals(1,jobs.size(),"character-mode is metadata, not a GenAPI request ID");
            assertFalse(jobs.containsKey("character-mode"));
            var kling=jobs.get("kling-video-o3");
            assertEquals("57020589",kling.get("requestId"));
            assertEquals("error",kling.get("status"));
            assertTrue(String.valueOf(kling.get("error")).contains("FILE_ERROR"));
            assertFalse(String.valueOf(kling.get("error")).contains("SECRET_TOKEN"));
            assertFalse(String.valueOf(kling.get("error")).contains("private.example.com"));
            var request=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
            verify(http,times(1)).send(request.capture(),any(HttpResponse.BodyHandler.class));
            assertEquals("GET",request.getValue().method());
            assertEquals("/api/v1/request/get/57020589",request.getValue().uri().getPath());
        }
    }

    private static final class AutoCloseablePilot implements AutoCloseable {
        final NeuroReplacementPilotService value;
        AutoCloseablePilot(NeuroReplacementPilotService value) { this.value = value; }
        @Override public void close() { value.close(); }
    }
}
