package ru.hothat.ai;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.hothat.config.ApiException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A duet must never spend or launch one paid seat without its partner. */
class AiVoiceDuetBudgetTest {
    private final AiVoiceSessionRepository repository=mock(AiVoiceSessionRepository.class);
    private final AiVoiceUsageService service=new AiVoiceUsageService(repository);
    private static final String ROOM="hat-test-duet";
    private static final String TEAM="test-team-2";
    private static final List<String> BOTS=List.of(
            "ai-gpt-live-first", "ai-gpt-live-second");
    private static final List<String> KEYS=List.of(
            "test-team-2:first:ai-gpt-live-first",
            "test-team-2:first:ai-gpt-live-second");

    private void budget(String amount) {
        ReflectionTestUtils.setField(service,"monthlyBudget",new BigDecimal(amount));
        when(repository.findByRoomIdAndGameNumberAndTurnKey(eq(ROOM),eq(1),anyString()))
                .thenReturn(Optional.empty());
        when(repository.findByStatusIn(anyList())).thenReturn(List.of());
        when(repository.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any(Instant.class)))
                .thenReturn(List.of());
        when(repository.save(any(AiVoiceSession.class))).thenAnswer(
                invocation->invocation.getArgument(0));
    }

    @Test
    void enoughForJustOneSeatRejectsWholePairWithoutAnyDatabaseWrites() {
        budget("0.30"); // one 180s hold is $0.20, two require $0.40
        assertThrows(ApiException.class,
                ()->service.reserveDuo(ROOM,1,TEAM,BOTS,KEYS));
        verify(repository,never()).save(any(AiVoiceSession.class));
    }

    @Test
    void enoughForBothSeatsCreatesTwoReservationsBeforeDispatch() {
        budget("0.40");
        List<AiVoiceSession> pair=service.reserveDuo(ROOM,1,TEAM,BOTS,KEYS);
        assertEquals(2,pair.size());
        assertEquals(BOTS,List.of(pair.get(0).getBotUid(),pair.get(1).getBotUid()));
        assertEquals(List.of("pending","pending"),
                List.of(pair.get(0).getStatus(),pair.get(1).getStatus()));
        assertNotEquals(pair.get(0).getSessionId(),pair.get(1).getSessionId());
        verify(repository,times(2)).save(any(AiVoiceSession.class));
    }

    @Test
    void existingSingleSeatCannotBeSilentlyTreatedAsCompletePair() {
        budget("1.00");
        AiVoiceSession old=AiVoiceSession.builder().sessionId("ai_orphan")
                .botUid(BOTS.get(0)).status("running").build();
        when(repository.findByRoomIdAndGameNumberAndTurnKey(ROOM,1,KEYS.get(0)))
                .thenReturn(Optional.of(old));
        assertThrows(ApiException.class,
                ()->service.reserveDuo(ROOM,1,TEAM,BOTS,KEYS));
        verify(repository,never()).save(any(AiVoiceSession.class));
    }

    @Test
    void finishedVoiceSpendingStillCountsTowardUnchangedMonthlyCap() {
        budget("0.50");
        AiVoiceSession earlier=AiVoiceSession.builder().sessionId("ai_finished")
                .status("finished").estimatedCostUsd(new BigDecimal("0.30"))
                .createdAt(Instant.now()).build();
        when(repository.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any(Instant.class)))
                .thenReturn(List.of(earlier));
        assertThrows(ApiException.class,
                ()->service.reserveDuo(ROOM,1,TEAM,BOTS,KEYS));
        verify(repository,never()).save(any(AiVoiceSession.class));
    }
}
