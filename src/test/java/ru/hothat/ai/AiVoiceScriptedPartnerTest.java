package ru.hothat.ai;

import org.junit.jupiter.api.Test;
import ru.hothat.config.ApiException;
import ru.hothat.game.domain.MatchEngine;
import ru.hothat.game.domain.MatchPhase;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.store.MatchStore;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.repository.GetterRoom;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Regression for a voice AI paired with an ordinary silent test-room bot. */
class AiVoiceScriptedPartnerTest {
    private static final String ROOM="hat-1234567890abcdef";
    private static final String TEAM="test-team-2";
    private static final String AI="ai-gpt-live-1234567890abcdef12";
    private static final String SCRIPTED="testbot-abcdef-3";

    private final MatchStore matches=mock(MatchStore.class);
    private final AiVoiceUsageService usage=mock(AiVoiceUsageService.class);
    private final MatchEngine engine=mock(MatchEngine.class);
    private final GetterRoom rooms=mock(GetterRoom.class);
    private final AiVoiceWordMemoryService memory=mock(AiVoiceWordMemoryService.class);
    private final AiVoiceGameService game=new AiVoiceGameService(matches,usage,engine,rooms,memory);

    private Room room(){
        return Room.builder().id(ROOM).isTestRoom(true)
                .testBotIds(new ArrayList<>(List.of(SCRIPTED))).build();
    }
    private void setUp(Room room){
        MatchState state=new MatchState(ROOM,false,true,false,Set.of(SCRIPTED),60);
        state.setPhase(MatchPhase.TURN_INTRO);
        state.setGameNumber(1);
        state.setCurrentTeamId(TEAM);
        state.setRosters(Map.of(TEAM,List.of(SCRIPTED,AI)));
        when(matches.read(ROOM)).thenReturn(state);
        when(usage.byId("ai_script_session")).thenReturn(Optional.of(
                AiVoiceSession.builder().sessionId("ai_script_session")
                    .roomId(ROOM).teamId(TEAM).botUid(AI).mode("game")
                    .status("running").gameNumber(1).build()));
        when(rooms.getById(ROOM)).thenReturn(Optional.of(room));
        when(rooms.getPlayer(ROOM,SCRIPTED)).thenReturn(Optional.of(
                RoomPlayer.builder().roomId(ROOM).teamId(TEAM).uid(SCRIPTED)
                    .isTestBot(true).build()));
        when(rooms.getPlayer(ROOM,AI)).thenReturn(Optional.of(
                RoomPlayer.builder().roomId(ROOM).teamId(TEAM).uid(AI)
                    .name("Шустрик").isTestBot(true).build()));
        when(memory.recent(ROOM,1,TEAM)).thenReturn(List.of());
    }

    @Test void validTestRoomScriptedPartnerHasNoAudioInputButOwnVoiceProfile(){
        setUp(room());
        Map<String,Object> context=game.context("ai_script_session");
        assertEquals(true,context.get("scriptedPartner"));
        assertEquals(false,context.get("aiDuo"));
        assertEquals(SCRIPTED,context.get("humanUid"));
        assertEquals("Шустрик",context.get("characterName"));
        assertEquals("beacon",context.get("voice"));
        assertEquals("unselected",context.get("role"));
    }
    @Test void cannotPairWithScriptedBotInOrdinaryRoom(){
        Room r=room();r.setIsTestRoom(false);setUp(r);
        assertThrows(ApiException.class,()->game.context("ai_script_session"));
    }
    @Test void cannotPairWithBotThatWasNotInServerAuthoritativeTestRoster(){
        Room r=room();r.setTestBotIds(new ArrayList<>());setUp(r);
        assertThrows(ApiException.class,()->game.context("ai_script_session"));
    }
}
