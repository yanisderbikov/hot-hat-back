package ru.hothat.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.hothat.common.livekit.LiveKitClient;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.repository.GetterRoom;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiVoiceSmallTalkServiceTest {
    private static final String ROOM="hat-1234567890abcdef";
    private static final String AI="ai-gpt-live-1234567890abcdef12";
    private static final String OWNER_UID="owner-user-1";
    private final GetterRoom rooms=mock(GetterRoom.class);
    private final AiVoiceUsageService usage=mock(AiVoiceUsageService.class);
    private final LiveKitClient livekit=mock(LiveKitClient.class);
    private final AiVoiceSmallTalkService service=
        new AiVoiceSmallTalkService(rooms,usage,livekit,new ObjectMapper());
    private final HotHatUser owner=new HotHatUser(OWNER_UID,null,null,true,true,false);
    private final HotHatUser ordinary=new HotHatUser(OWNER_UID,null,null,false,false,false);

    private void eligible(){
        when(rooms.getById(ROOM)).thenReturn(Optional.of(
            Room.builder().id(ROOM).phase("setup").gameNumber(0).build()));
        when(rooms.getPlayer(ROOM,AI)).thenReturn(Optional.of(
            RoomPlayer.builder().roomId(ROOM).uid(AI).teamId("team-a").isTestBot(true).build()));
        when(rooms.getPlayer(ROOM,OWNER_UID)).thenReturn(Optional.of(
            RoomPlayer.builder().roomId(ROOM).uid(OWNER_UID).teamId("team-a").isTestBot(false).build()));
    }
    private void enabled(){
        ReflectionTestUtils.setField(service,"featureEnabled",true);
        ReflectionTestUtils.setField(service,"dispatchEnabled",true);
        ReflectionTestUtils.setField(service,"workerSecret","this-secret-is-at-least-32-characters");
    }

    @Test
    void onlyServiceOwnerCanReadOrStartOrStopPaidSmallTalk(){
        assertThrows(ApiException.class,()->service.status(ordinary,ROOM,AI));
        assertThrows(ApiException.class,()->service.start(ordinary,ROOM,AI));
        assertThrows(ApiException.class,()->service.stop(ordinary,ROOM,AI));
        verifyNoInteractions(rooms,usage,livekit);
    }

    @Test
    void ownerNeedsActualRoomMicrophoneSeat(){
        when(rooms.getById(ROOM)).thenReturn(Optional.of(
            Room.builder().id(ROOM).phase("setup").gameNumber(0).build()));
        when(rooms.getPlayer(ROOM,AI)).thenReturn(Optional.of(
            RoomPlayer.builder().roomId(ROOM).uid(AI).build()));
        assertThrows(ApiException.class,()->service.start(owner,ROOM,AI));
        verifyNoInteractions(usage,livekit);
    }

    @Test
    void ownerDispatchesExactlyOneBoundedOffTurnJob(){
        eligible();enabled();
        AiVoiceSession s=AiVoiceSession.builder().sessionId("ai_test_session")
            .roomId(ROOM).botUid(AI).mode("smalltalk").speakerUid(OWNER_UID)
            .status("running").build();
        when(usage.activeSessions()).thenReturn(List.of(),List.of(s));
        when(usage.reserve(eq(ROOM),eq(0),eq("team-a"),eq(AI),anyString(),eq(OWNER_UID)))
            .thenReturn(s);
        when(livekit.createAiDispatch(eq(ROOM),anyString()))
            .thenReturn(Map.of("id","dispatch-1"));

        var answer=service.start(owner,ROOM,AI);

        assertEquals(true,answer.get("active"));
        verify(livekit).createAiDispatch(eq(ROOM),argThat(metadata->
            metadata.contains("\"mode\":\"smalltalk\"")
                && metadata.contains("\"speakerUid\":\""+OWNER_UID+"\"")
                && metadata.contains("\"maxSessionSeconds\":180")));
        verify(usage).markDispatched("ai_test_session","dispatch-1");
    }

    @Test
    void noOwnerSmallTalkWhenRublishaIsAlreadyActiveTeam() {
        when(rooms.getById(ROOM)).thenReturn(Optional.of(
            Room.builder().id(ROOM).phase("active").gameNumber(1)
                .currentTeamId("team-a").build()));
        when(rooms.getPlayer(ROOM,AI)).thenReturn(Optional.of(
            RoomPlayer.builder().roomId(ROOM).uid(AI)
                .teamId("team-a").isTestBot(true).build()));
        when(rooms.getPlayer(ROOM,OWNER_UID)).thenReturn(Optional.of(
            RoomPlayer.builder().roomId(ROOM).uid(OWNER_UID)
                .teamId("team-a").isTestBot(false).build()));
        enabled();
        assertThrows(ApiException.class,()->service.start(owner,ROOM,AI));
        verifyNoInteractions(usage,livekit);
    }

    @Test
    void ownerCanStopOnlyTheirManualVoiceSession(){
        eligible();
        AiVoiceSession s=AiVoiceSession.builder().sessionId("ai_test_session")
            .roomId(ROOM).botUid(AI).mode("smalltalk").speakerUid(OWNER_UID)
            .status("running").livekitDispatchId("dispatch-1").build();
        when(usage.activeSessions()).thenReturn(List.of(s));

        var result=service.stop(owner,ROOM,AI);

        assertEquals(false,result.get("active"));
        verify(usage).beginStopping("ai_test_session");
        verify(livekit).deleteAiDispatch(ROOM,"dispatch-1");
    }
}
