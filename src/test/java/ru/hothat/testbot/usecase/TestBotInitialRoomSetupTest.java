package ru.hothat.testbot.usecase;

import org.junit.jupiter.api.Test;
import ru.hothat.config.HotHatUser;
import ru.hothat.media.spi.MemeLibraryPort;
import ru.hothat.model.room.Room;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.service.game.GameService;
import ru.hothat.service.livekit.LiveKitService;
import ru.hothat.service.testbots.impl.TestBotsServiceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regression for the actual bug: a NEW test room already has isTestRoom=true. */
class TestBotInitialRoomSetupTest {
    private static final String ROOM="hat-0123456789abcdef";
    private final GetterRoom read=mock(GetterRoom.class);
    private final SaverRoom write=mock(SaverRoom.class);
    private final MemeLibraryPort memes=mock(MemeLibraryPort.class);
    private final TestBotsServiceImpl service = new TestBotsServiceImpl(read,write,memes,
            mock(LiveKitService.class), mock(GameService.class));
    private final HotHatUser host=mock(HotHatUser.class);

    private Room room(){
        when(host.uid()).thenReturn("host-1");
        Room room=Room.builder().id(ROOM).createdBy("host-1")
                .testOwnerUid("host-1").isTestRoom(true).phase("setup")
                .gameMode("sabotage").maxPlayers(4)
                .teamOrder(new ArrayList<>()).testBotIds(new ArrayList<>()).build();
        when(read.getById(ROOM)).thenReturn(Optional.of(room));
        when(read.getWordSubmissions(ROOM)).thenReturn(List.of());
        when(memes.catalog(anyInt())).thenReturn(List.of());
        return room;
    }

    @Test
    void newlyCreatedTestRoomMustCreateEveryMissingBotAndEveryTeam(){
        Room room=room();
        Map<String,Object> result=service.setup(host,ROOM);
        assertThat(room.getIsTestRoom()).isTrue();
        assertThat(room.getTestBotIds()).hasSize(3);
        assertThat(room.getTeamOrder()).hasSize(2);
        assertThat(result.get("botIds")).isEqualTo(room.getTestBotIds());
        assertThat(room.getTestBotIds()).containsExactly(
                "testbot-abcdef-1","testbot-abcdef-2","testbot-abcdef-3");
        verify(write,times(3)).savePlayer(argThat(bot->
                Boolean.TRUE.equals(bot.getIsTestBot())));
        verify(write,times(2)).saveTeam(any());
        verify(write).save(room);
    }

    @Test
    void replayOfSetupNeverRecreatesManuallyReleasedBots(){
        Room room=room();
        room.setTeamOrder(new ArrayList<>(List.of("test-team-1","test-team-2")));
        room.setTestBotIds(new ArrayList<>(List.of("testbot-abcdef-1")));
        Map<String,Object> result=service.setup(host,ROOM);
        assertThat(result.get("botIds")).isEqualTo(List.of("testbot-abcdef-1"));
        verify(write,never()).savePlayer(any());
        verify(write,never()).saveTeam(any());
        verify(write,never()).save(any());
    }
}
