package ru.hothat.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.hothat.common.livekit.LiveKitClient;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.repository.GetterRoom;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The voice worker must be explicitly dispatched for the current AI team. */
class AiVoiceDispatchSchedulerTest {
    private static final String ROOM_ID="hat-1234567890abcdef";
    private static final String HUMAN="human-1";
    private static final String BOT="ai-gpt-live-1234567890abcdef12";
    private static final String TEAM="team-1";

    private final GetterRoom rooms=mock(GetterRoom.class);
    private final AiVoiceUsageService usage=mock(AiVoiceUsageService.class);
    private final LiveKitClient livekit=mock(LiveKitClient.class);
    private final AiVoiceDispatchScheduler scheduler=
            new AiVoiceDispatchScheduler(rooms,usage,livekit,new ObjectMapper());

    private Room activeRoom(){
        return Room.builder()
                .id(ROOM_ID).phase("turnIntro").gameNumber(1)
                .ranked(false).gamePaused(false).currentTeamId(TEAM)
                .teamRosters(Map.of(TEAM,List.of(HUMAN,BOT)))
                .build();
    }

    private void enable(){
        ReflectionTestUtils.setField(scheduler,"featureEnabled",true);
        ReflectionTestUtils.setField(scheduler,"dispatchEnabled",true);
        ReflectionTestUtils.setField(scheduler,"workerSecret","this_is_a_sufficiently_long_private_worker_key");
    }

    @Test
    void dispatchesVoiceWhenAiHumanPairIsTheCurrentTeam() {
        enable();
        when(rooms.getByPhase("turnIntro",100)).thenReturn(List.of(activeRoom()));
        when(rooms.getByPhase("active",100)).thenReturn(List.of());
        when(rooms.getPlayer(ROOM_ID,HUMAN))
                .thenReturn(Optional.of(RoomPlayer.builder().uid(HUMAN).roomId(ROOM_ID)
                        .teamId(TEAM).isTestBot(false).build()));
        when(usage.activeSessions()).thenReturn(List.of());
        when(usage.reserve(eq(ROOM_ID),eq(1),eq(TEAM),eq(BOT),anyString()))
                .thenReturn(AiVoiceSession.builder().sessionId("ai_session_one")
                        .status("pending").build());
        when(livekit.createAiDispatch(eq(ROOM_ID),anyString()))
                .thenReturn(Map.of("id","dispatch-1"));

        scheduler.reconcile();

        verify(livekit).createAiDispatch(eq(ROOM_ID),argThat(s ->
                s.contains("ai_session_one") && s.contains(BOT) &&
                        s.contains("\"maxSessionSeconds\":180")));
        verify(usage).markDispatched("ai_session_one","dispatch-1");
    }

    @Test
    void dispatchesVoiceForAiWithOneSilentScriptedTeammateInTestRoom() {
        enable();
        String scripted="testbot-abcdef-3";
        Room room=activeRoom();
        room.setIsTestRoom(true);
        room.setTestBotIds(new java.util.ArrayList<>(List.of(scripted)));
        room.setTeamRosters(Map.of(TEAM,List.of(scripted,BOT)));
        when(rooms.getByPhase("turnIntro",100)).thenReturn(List.of(room));
        when(rooms.getByPhase("active",100)).thenReturn(List.of());
        when(rooms.getPlayer(ROOM_ID,scripted)).thenReturn(Optional.of(
                RoomPlayer.builder().uid(scripted).roomId(ROOM_ID)
                    .teamId(TEAM).isTestBot(true).build()));
        when(usage.activeSessions()).thenReturn(List.of());
        when(usage.reserve(eq(ROOM_ID),eq(1),eq(TEAM),eq(BOT),anyString()))
                .thenReturn(AiVoiceSession.builder().sessionId("ai_scripted_session")
                    .status("pending").build());
        when(livekit.createAiDispatch(eq(ROOM_ID),anyString()))
                .thenReturn(Map.of("id","dispatch-scripted"));

        scheduler.reconcile();
        verify(livekit).createAiDispatch(eq(ROOM_ID),argThat(metadata->
                metadata.contains(BOT) && metadata.contains("ai_scripted_session")));
    }

    @Test
    void willNotDispatchScriptedBotPartnerInNonTestRoom(){
        enable();
        String scripted="testbot-abcdef-3";
        Room room=activeRoom();
        room.setTeamRosters(Map.of(TEAM,List.of(scripted,BOT)));
        when(rooms.getByPhase("turnIntro",100)).thenReturn(List.of(room));
        when(rooms.getByPhase("active",100)).thenReturn(List.of());
        when(rooms.getPlayer(ROOM_ID,scripted)).thenReturn(Optional.of(
                RoomPlayer.builder().uid(scripted).roomId(ROOM_ID)
                    .teamId(TEAM).isTestBot(true).build()));
        scheduler.reconcile();
        verify(livekit,never()).createAiDispatch(anyString(),anyString());
    }

    @Test
    void neverStartsPaidInferenceWhenDispatchNotEnabled() {
        ReflectionTestUtils.setField(scheduler,"featureEnabled",true);
        ReflectionTestUtils.setField(scheduler,"dispatchEnabled",false);
        ReflectionTestUtils.setField(scheduler,"workerSecret",
                "this_is_a_sufficiently_long_private_worker_key");
        scheduler.reconcile();
        verifyNoInteractions(rooms,usage,livekit);
    }

    @Test
    void oneActiveSessionBlocksSecondPaidDispatch() {
        enable();
        when(rooms.getByPhase("turnIntro",100)).thenReturn(List.of(activeRoom()));
        when(rooms.getByPhase("active",100)).thenReturn(List.of());
        when(rooms.getPlayer(ROOM_ID,HUMAN)).thenReturn(Optional.of(
                RoomPlayer.builder().uid(HUMAN).roomId(ROOM_ID).isTestBot(false).build()));
        when(usage.activeSessions()).thenReturn(List.of(
                AiVoiceSession.builder().sessionId("ai_existing").status("running").build()));
        scheduler.reconcile();
        verify(livekit,never()).createAiDispatch(anyString(),anyString());
    }
}
