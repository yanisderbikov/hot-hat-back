package ru.hothat.ai;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiVoiceWordMemoryServiceTest {
    private final AiVoiceWordMemoryRepository repo=mock(AiVoiceWordMemoryRepository.class);
    private final AiVoiceWordMemoryService service=new AiVoiceWordMemoryService(repo);
    @Test void onlyConfirmedWordsAreRemembered() {
        service.record("hat-one",1,"team-one","turn-one","action-one","ракета","guessed",false);
        service.record("hat-one",1,"team-one","turn-one","action-one","ракета","guessed",true);
        verify(repo,times(1)).save(argThat(row->row.getWord().equals("ракета") &&
            row.getGameNumber()==1 && row.getOutcome().equals("guessed")));
    }
    @Test void retryWithSameActionIdDoesNotDuplicate() {
        when(repo.existsByRoomIdAndGameNumberAndTurnIdAndActionId(
            "hat-one",1,"turn-one","action-one")).thenReturn(true);
        service.record("hat-one",1,"team-one","turn-one","action-one","слон","guessed",true);
        verify(repo,never()).save(any());
    }
    @Test void secretsAndInvalidOutcomesCannotBeRecorded() {
        service.record("hat-one",1,"team-one","turn-one","action-one","секрет","pending",true);
        service.record("hat-one",1,"team-one","turn-one","action-one",null,"guessed",true);
        verifyNoInteractions(repo);
    }
    @Test void onlySpecifiedTeamAndGameAreRecalled() {
        when(repo.findTop40ByRoomIdAndGameNumberAndTeamIdOrderByRecordedAtDesc("hat-one",2,"team-one"))
            .thenReturn(List.of(AiVoiceWordMemory.builder().id("id").roomId("hat-one")
                .gameNumber(2).teamId("team-one").turnId("turn-two").actionId("action-two")
                .word("будильник").outcome("guessed").recordedAt(Instant.now()).build()));
        List<Map<String,String>> rows=service.recent("hat-one",2,"team-one");
        assertEquals(1,rows.size());
        assertEquals("будильник",rows.get(0).get("word"));
        assertEquals("guessed",rows.get(0).get("outcome"));
        verify(repo).findTop40ByRoomIdAndGameNumberAndTeamIdOrderByRecordedAtDesc("hat-one",2,"team-one");
    }
    @Test void noHistoryBeforeStartOfMatch() {
        assertTrue(service.recent("hat-one",0,"team-one").isEmpty());
        verifyNoInteractions(repo);
    }
}
