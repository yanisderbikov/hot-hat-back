package ru.hothat.testbot.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import ru.hothat.common.storage.ObjectStore;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NeuroUploadResumeTest {
    private static final String JOB="c4b22d85-6ba7-4fc9-9a3a-7582a3742456";
    private static final String OWNER="owner-1";
    private static final String ID="57179646";

    @SuppressWarnings({"unchecked","rawtypes"})
    private static Setup service(String providerState) throws Exception {
        HttpClient http=mock(HttpClient.class);
        HttpResponse<String> reply=mock(HttpResponse.class);
        when(reply.statusCode()).thenReturn(200);
        when(reply.body()).thenReturn("{\"status\":\""+providerState+"\",\"result\":[\"https://gen-api.storage.yandexcloud.net/finished.mp4\"]}");
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class)))
                .thenReturn(reply);
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        ResultSet row=mock(ResultSet.class);
        when(row.getString(1)).thenReturn(JOB);
        when(row.getString(2)).thenReturn("hat-fac7edupload872aed5274624799b23afb09b5a16a4c");
        when(row.getString(3)).thenReturn(OWNER);
        when(row.getString(4)).thenReturn("failed");
        when(row.getString(5)).thenReturn("GenAPI: timeout");
        when(row.getString(6)).thenReturn(
            "{\"character-mode\":\"genapi-upload-video-10\",\"kling-video-o3\":\""+ID+"\"}");
        when(row.getString(8)).thenReturn("GenAPI kling-video-o3 timeout (30 мин.), request_id="+ID);
        when(row.getString(10)).thenReturn("neuro-replacements/owner-1/source.webm");
        when(row.getString(11)).thenReturn("neuro-replacements/owner-1/voice.wav");
        when(row.getString(12)).thenReturn("video/webm");
        when(row.getString(13)).thenReturn("audio/wav");
        when(row.getString(16)).thenReturn("Повернуться и сказать «А вы всё ещё кипятите?»");
        when(jdbc.query(anyString(),any(RowMapper.class),eq(JOB)))
                .thenAnswer(call->List.of(((RowMapper)call.getArgument(1)).mapRow(row,0)));
        ObjectStore store=mock(ObjectStore.class);
        when(store.configured()).thenReturn(true);
        NeuroReplacementPilotService pilot=new NeuroReplacementPilotService(new ObjectMapper(),http);
        pilot.setJdbcTemplate(jdbc);
        pilot.setObjectStore(store);
        org.springframework.test.util.ReflectionTestUtils.setField(pilot,"key","test-genapi-key");
        return new Setup(pilot,http,jdbc);
    }
    private record Setup(NeuroReplacementPilotService pilot,HttpClient http,JdbcTemplate jdbc)
            implements AutoCloseable {
        @Override public void close(){pilot.close();}
    }

    @Test
    void stillProcessingAfterLocal30MinuteTimeoutOnlyPerformsFreeGet() throws Exception {
        try(Setup s=service("processing")){
            Map<String,Object> response=s.pilot.resumeTimedOutUpload(OWNER,JOB);
            assertEquals(false,response.get("recoveryStarted"));
            assertEquals("processing",response.get("providerStatus"));
            assertEquals("failed",response.get("status"));
            var requests=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
            verify(s.http,times(1)).send(requests.capture(),
                    any(HttpResponse.BodyHandler.class));
            assertEquals("GET",requests.getValue().method());
            assertTrue(requests.getValue().uri().getPath().endsWith("/request/get/"+ID));
            verify(s.jdbc,never()).update(anyString(),any(Object[].class));
        }
    }

    @Test
    void alreadyClaimedSuccessCannotDoubleStartDownstreamStages() throws Exception {
        try(Setup s=service("success")){
            // A second browser tab loses the atomic claim.
            when(s.jdbc.update(anyString(),any(Object[].class))).thenReturn(0);
            Map<String,Object> result=s.pilot.resumeTimedOutUpload(OWNER,JOB);
            assertEquals("failed",result.get("status"));
            var requests=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
            verify(s.http,times(1)).send(requests.capture(),
                    any(HttpResponse.BodyHandler.class));
            assertEquals("GET",requests.getValue().method());
            verify(s.jdbc,times(1)).update(anyString(),any(Object[].class));
        }
    }

    @Test
    void wrongOwnerNeverContactsProvider() throws Exception {
        try(Setup s=service("success")){
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    ()->s.pilot.resumeTimedOutUpload("another-owner",JOB));
            verifyNoInteractions(s.http);
            verify(s.jdbc,never()).update(anyString(),any(Object[].class));
        }
    }
}
