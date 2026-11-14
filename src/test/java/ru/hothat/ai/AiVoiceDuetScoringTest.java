package ru.hothat.ai;

import org.junit.jupiter.api.Test;
import ru.hothat.game.domain.MatchEngine;
import ru.hothat.game.domain.MatchPhase;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.domain.WordResolved;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.repository.GetterRoom;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** SCRUM-52: score only authentic spoken guesses from the AI GUESSER. */
class AiVoiceDuetScoringTest {
    private static final String ROOM="hat-1234567890abcdef";
    private static final String TEAM="team-duet";
    private static final String EXPLAINER="ai-gpt-live-aaaaaaaaaaaaaaaaaa";
    private static final String GUESSER="ai-gpt-live-bbbbbbbbbbbbbbbbbb";

    private record Fixture(AiVoiceGameService service, MatchEngine engine,
                           MatchSession transaction) {}
    private Fixture make(String botUid) {
        MatchStore matches=mock(MatchStore.class);
        AiVoiceUsageService usage=mock(AiVoiceUsageService.class);
        MatchEngine engine=mock(MatchEngine.class);
        GetterRoom rooms=mock(GetterRoom.class);
        AiVoiceWordMemoryService memory=mock(AiVoiceWordMemoryService.class);
        MatchState game=new MatchState(ROOM,false,true,false,Set.of(),60);
        game.setGameNumber(1);
        game.setPhase(MatchPhase.ACTIVE);
        game.setCurrentTeamId(TEAM);
        game.setTurnId("turn-1");
        game.setCurrentWord("Компас");
        game.setExplainerUid(EXPLAINER);
        game.setGuesserUid(GUESSER);
        game.setRosters(Map.of(TEAM,List.of(EXPLAINER,GUESSER)));
        MatchSession txn=mock(MatchSession.class);
        when(txn.state()).thenReturn(game);
        when(matches.read(ROOM)).thenReturn(game);
        when(matches.open(ROOM)).thenReturn(txn);
        when(usage.byId("ai_voice")).thenReturn(Optional.of(
                AiVoiceSession.builder().sessionId("ai_voice")
                    .roomId(ROOM).teamId(TEAM).botUid(botUid).mode("game")
                    .status("running").gameNumber(1).build()));
        for(String uid:List.of(EXPLAINER,GUESSER)) {
            when(rooms.getPlayer(ROOM,uid)).thenReturn(Optional.of(
                    RoomPlayer.builder().roomId(ROOM).teamId(TEAM)
                        .uid(uid).name("ИИ").isTestBot(true).build()));
        }
        when(engine.guess(eq(game),eq("turn-1"),anyString())).thenReturn(
            new WordResolved(WordResolved.Outcome.COUNTED,
                             "Компас","Луна",1,6,1,false,0));
        return new Fixture(new AiVoiceGameService(matches,usage,engine,rooms,memory),
                           engine,txn);
    }

    @Test void genuineAiGuesserSpeechScoresOneWordAndTeamPoint() {
        Fixture f=make(GUESSER);
        Map<String,Object> result=f.service().guessedByDuetVoice(
                "ai_voice","Это компас?","aig_123456789012345678");
        assertEquals(true,result.get("matched"));
        verify(f.engine(),times(1)).guess(any(),eq("turn-1"),anyString());
        verify(f.transaction(),times(1)).addTeamScore(TEAM,1);
        verify(f.transaction(),times(1)).commit();
        assertFalse(result.containsKey("word")); // secret stays server-side
    }

    @Test void fullRussianCaseFormsScoreButUnrelatedOrDerivedWordsDoNot() {
        for(String[] pair:List.of(
                new String[]{"космонавт","А это космонавта!"},
                new String[]{"пылесос","Пылесосом, наверное."},
                new String[]{"путешествие","Какое путешествия?"},
                new String[]{"снежный человек","Это снежного человека!"},
                new String[]{"секретный агент","А это секретного агента"},
                new String[]{"чемодан","У чемодана есть ручка"},
                new String[]{"детектив","Детектива?"})) {
            assertTrue(AiVoiceGameService.exactWordSpoken(pair[0],pair[1]),
                    pair[0]+" should match "+pair[1]);
        }
        for(String[] pair:List.of(
                new String[]{"кот","котик"},
                new String[]{"пылесос","пылесосик"},
                new String[]{"снежный человек","снежная королева"},
                new String[]{"космонавт","космонавтика"},
                new String[]{"секретный агент","секретное агентство"},
                new String[]{"компас","компост"})) {
            assertFalse(AiVoiceGameService.exactWordSpoken(pair[0],pair[1]),
                    pair[0]+" must reject "+pair[1]);
        }
    }

    @Test void explainerCannotSubmitHisOwnSecretAsGuesser() {
        Fixture f=make(EXPLAINER);
        assertEquals("DUET_GUESSER_ONLY",f.service().guessedByDuetVoice(
            "ai_voice","Компас","aig_123456789012345678").get("reason"));
        verify(f.engine(),never()).guess(any(),anyString(),anyString());
        verify(f.transaction(),never()).commit();
    }

    @Test void expiredSpokenAnswerNeverCountsOrTriggersCorrectConfirmation() {
        Fixture f=make(GUESSER);
        when(f.engine().guess(any(),eq("turn-1"),anyString())).thenReturn(
                new WordResolved(WordResolved.Outcome.TURN_EXPIRED,
                        "Компас",null,0,5,0,true,0));
        Map<String,Object> result=f.service().guessedByDuetVoice(
                "ai_voice","Компас!","aig_123456789012345678");
        assertEquals(false,result.get("matched"));
        assertEquals("NOT_COUNTED",result.get("reason"));
        assertEquals(true,result.get("turnClosed"));
        verify(f.transaction(),never()).addTeamScore(anyString(),anyInt());
        // Expired turn's transition must still be persisted.
        verify(f.transaction(),times(1)).commit();
    }

    @Test void incorrectGuessesAndSubstringsNeverAwardPoints() {
        Fixture f=make(GUESSER);
        for(String guess:List.of("Это карта","Компасик","Угадали!")) {
            assertEquals("FORM_NOT_EXACT",f.service().guessedByDuetVoice(
                "ai_voice",guess,"aig_123456789012345678").get("reason"));
        }
        verify(f.engine(),never()).guess(any(),anyString(),anyString());
        verify(f.transaction(),never()).commit();
    }
}
