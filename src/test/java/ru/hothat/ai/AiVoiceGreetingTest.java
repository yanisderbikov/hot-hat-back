package ru.hothat.ai;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiVoiceGreetingTest {
    private final AiVoiceSessionRepository repo=mock(AiVoiceSessionRepository.class);
    private final AiVoiceUsageService usage=new AiVoiceUsageService(repo);
    private static final String ROOM="hat-1234567890abcdef";

    @Test void firstSessionOfGameGreetsOnce() {
        AiVoiceSession current=AiVoiceSession.builder().sessionId("ai_new").status("pending").build();
        when(repo.findByRoomIdAndGameNumberOrderByCreatedAtDesc(ROOM,1)).thenReturn(List.of(current));
        assertTrue(usage.shouldGreet(ROOM,1,"ai_new"));
    }

    @Test void workerRestartDoesNotRepeatGreetingEvenAfterVoiceFailure() {
        AiVoiceSession old=AiVoiceSession.builder().sessionId("ai_old").status("failed")
            .startedAt(Instant.now().minusSeconds(120)).build();
        AiVoiceSession current=AiVoiceSession.builder().sessionId("ai_new").status("pending").build();
        when(repo.findByRoomIdAndGameNumberOrderByCreatedAtDesc(ROOM,2))
            .thenReturn(List.of(current,old));
        assertFalse(usage.shouldGreet(ROOM,2,"ai_new"));
    }

    @Test void failedDispatchNeverReachedAudioAndCanRetryGreeting() {
        AiVoiceSession failed=AiVoiceSession.builder().sessionId("ai_failed")
            .status("failed").build();
        AiVoiceSession retry=AiVoiceSession.builder().sessionId("ai_retry")
            .status("pending").build();
        when(repo.findByRoomIdAndGameNumberOrderByCreatedAtDesc(ROOM,3))
            .thenReturn(List.of(retry,failed));
        assertTrue(usage.shouldGreet(ROOM,3,"ai_retry"));
    }
}
