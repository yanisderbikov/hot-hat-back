package ru.hothat.ai;

import org.junit.jupiter.api.Test;
import ru.hothat.config.ApiException;
import ru.hothat.game.domain.MatchEngine;
import ru.hothat.game.domain.MatchPhase;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.repository.GetterRoom;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** Real dual-AI roles are server-owned: the guessing agent may not
 * steal explainer role even if an LLM invokes the wrong function tool.
 */
class AiVoiceDuoRoleTest {
    private static final String ROOM="hat-1234567890abcdef";
    private static final String TEAM="team-duo";
    private static final String EXPLAINER="ai-gpt-live-aaaaaaaaaaaaaaaaaa";
    private static final String GUESSER="ai-gpt-live-bbbbbbbbbbbbbbbbbb";

    @Test void guesserCannotClaimExplainerRoleOrStartTwoCompetingTurns(){
        MatchStore matches=mock(MatchStore.class);
        AiVoiceUsageService usage=mock(AiVoiceUsageService.class);
        MatchEngine engine=mock(MatchEngine.class);
        GetterRoom rooms=mock(GetterRoom.class);
        AiVoiceWordMemoryService memory=mock(AiVoiceWordMemoryService.class);
        AiVoiceGameService service=new AiVoiceGameService(matches,usage,engine,rooms,memory);

        MatchState game=new MatchState(ROOM,false,true,false,Set.of(),60);
        game.setGameNumber(1);
        game.setPhase(MatchPhase.TURN_INTRO);
        game.setCurrentTeamId(TEAM);
        game.setRosters(Map.of(TEAM,List.of(EXPLAINER,GUESSER)));
        MatchSession txn=mock(MatchSession.class);
        when(txn.state()).thenReturn(game);
        when(matches.read(ROOM)).thenReturn(game);
        when(matches.open(ROOM)).thenReturn(txn);
        when(usage.byId("ai_duo_guesser")).thenReturn(Optional.of(
                AiVoiceSession.builder().sessionId("ai_duo_guesser")
                    .roomId(ROOM).teamId(TEAM).botUid(GUESSER).mode("game")
                    .status("running").gameNumber(1).build()));
        when(rooms.getPlayer(ROOM,EXPLAINER)).thenReturn(Optional.of(
                RoomPlayer.builder().roomId(ROOM).teamId(TEAM)
                    .uid(EXPLAINER).name("Бубнил").isTestBot(true).build()));
        when(rooms.getPlayer(ROOM,GUESSER)).thenReturn(Optional.of(
                RoomPlayer.builder().roomId(ROOM).teamId(TEAM)
                    .uid(GUESSER).name("Шустрик").isTestBot(true).build()));

        assertThrows(ApiException.class,()->service.aiChoosesToExplain("ai_duo_guesser"));
        verify(engine,never()).beginTurn(any(),anyString());
        verify(txn,never()).commit();
    }
}
