package ru.hothat.testbot.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import ru.hothat.common.storage.ObjectStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regression for owner-paid Kling 57716859: preflight HTTP 404 occurred
 * because public reference allowed only *-kling-reference.mp4, but the
 * voice rescue correctly linked *-paid-kling.mp4.
 */
class NeuroPaidKlingReferenceRescueContractTest {
    private final String owner="owner-test";
    private final String job="c1aec325-5dc2-4b38-b395-01d9dedc8a19";
    private final String token="a".repeat(64);

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void authorizedPaidKlingReferenceIsDownloadableViaExistingTokenWithoutProviderCall()
            throws Exception {
        NeuroReplacementPilotService pilot=new NeuroReplacementPilotService(new ObjectMapper());
        try {
            JdbcTemplate db=mock(JdbcTemplate.class);
            ObjectStore store=mock(ObjectStore.class);
            when(store.configured()).thenReturn(true);
            pilot.setJdbcTemplate(db);
            pilot.setObjectStore(store);
            String s3Key="neuro-replacements/"+owner+"/"+job+"-paid-kling.mp4";
            when(db.query(anyString(),any(RowMapper.class),eq(token)))
                    .thenReturn(List.of(s3Key));
            byte[] paidMp4=new byte[1024];
            when(store.get(s3Key)).thenReturn(paidMp4);
            assertArrayEquals(paidMp4,pilot.publicKlingReference(token));
            verify(store,times(1)).get(s3Key);
            // Only private object store GET, NEVER a paid provider POST.
        } finally {
            pilot.close();
        }
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void sameCapabilityTokenMustNotExposeOtherBucketObjects() throws Exception {
        NeuroReplacementPilotService pilot=new NeuroReplacementPilotService(new ObjectMapper());
        try {
            JdbcTemplate db=mock(JdbcTemplate.class);
            ObjectStore store=mock(ObjectStore.class);
            when(store.configured()).thenReturn(true);
            pilot.setJdbcTemplate(db);
            pilot.setObjectStore(store);
            when(db.query(anyString(),any(RowMapper.class),eq(token)))
                    .thenReturn(List.of("neuro-replacements/"+owner+"/private-config.mp4"));
            ResponseStatusException error=assertThrows(ResponseStatusException.class,
                    ()->pilot.publicKlingReference(token));
            assertEquals(HttpStatus.NOT_FOUND,error.getStatusCode());
            verify(store,never()).get(anyString());
        } finally {
            pilot.close();
        }
    }
}
