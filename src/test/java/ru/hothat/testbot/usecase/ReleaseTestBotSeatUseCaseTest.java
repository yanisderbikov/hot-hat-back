package ru.hothat.testbot.usecase;

import org.junit.jupiter.api.Test;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.usecase.RoomAccessGuard;
import ru.hothat.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ReleaseTestBotSeatUseCaseTest {
    private static final String ID="hat-0123456789abcdef",HOST="host-1";
    private static final String BOT1="testbot-abcdef-1",BOT2="testbot-abcdef-2";
    private final RoomAccessGuard access=mock(RoomAccessGuard.class);
    private final GetterRoom read=mock(GetterRoom.class);
    private final SaverRoom write=mock(SaverRoom.class);
    private final TestRoomFeatureGuard flags=mock(TestRoomFeatureGuard.class);
    private final ReleaseTestBotSeatUseCase action=
            new ReleaseTestBotSeatUseCase(access,read,write,flags);
    private final HotHatUser owner=mock(HotHatUser.class);

    private Room setup(){
        when(owner.uid()).thenReturn(HOST);
        Room room=Room.builder().id(ID).createdBy(HOST).testOwnerUid(HOST)
                .isTestRoom(true).phase("setup")
                .testBotIds(new ArrayList<>(List.of(BOT1,BOT2)))
                .teamOrder(new ArrayList<>(List.of("team-1")))
                .testBotRuntime(new LinkedHashMap<>(Map.of(
                        BOT1,Map.of("name","Bot1"),BOT2,Map.of("name","Bot2")))).build();
        when(access.requireHostRoomForWrite(owner,ID)).thenReturn(room);
        RoomPlayer bot=RoomPlayer.builder().roomId(ID).uid(BOT2)
                .isTestBot(true).teamId("team-1")
                .memeLoadout(List.of("a","b","c","d","e")).build();
        when(read.getPlayer(ID,BOT2)).thenReturn(Optional.of(bot));
        when(read.getTeam(ID,"team-1")).thenReturn(Optional.of(
                RoomTeam.builder().roomId(ID).teamId("team-1")
                        .memberUids(new ArrayList<>(List.of(BOT1,BOT2))).build()));
        return room;
    }

    @Test
    void onlyOneBotIsRemovedOtherBotsKeepPlayingAndReservationPersists(){
        Room room=setup();
        var freed=action.run(owner,ID,BOT2);
        assertThat(freed.botUid()).isEqualTo(BOT2);
        assertThat(freed.teamId()).isEqualTo("team-1");
        assertThat(room.getTestBotIds()).containsExactly(BOT1);
        assertThat(room.getTestBotRuntime()).containsKey(BOT1).doesNotContainKey(BOT2);
        Map<String,Object> holds=Json.map(room.getTestBotRuntime().get(
                ReleaseTestBotSeatUseCase.RESERVED_SLOTS));
        assertThat(holds).containsKey(BOT2);
        assertThat(Json.map(holds.get(BOT2)).get("memeLoadout"))
                .isEqualTo(List.of("a","b","c","d","e"));
        verify(write).deletePlayer(ID,BOT2);
        verify(write,never()).deletePlayer(ID,BOT1);
        verify(write).save(room);
        // Important: team roster keeps bot UID as an OPEN SLOT marker,
        // not as an actual player. StartMatch reads live RoomPlayer rows.
        verify(write,never()).saveTeam(any());
    }

    @Test
    void cannotReleaseAlreadyReplacedOrUnknownBot(){
        Room room=setup();
        room.setTestBotIds(new ArrayList<>(List.of(BOT1)));
        assertThatThrownBy(()->action.run(owner,ID,BOT2))
                .isInstanceOf(RuntimeException.class);
        verify(write,never()).deletePlayer(anyString(),anyString());
    }

    @Test
    void cannotRemoveAnyBotAfterMatchStarted(){
        Room room=setup();
        room.setPhase("active");
        assertThatThrownBy(()->action.run(owner,ID,BOT2))
                .isInstanceOf(RuntimeException.class);
        verify(write,never()).deletePlayer(anyString(),anyString());
    }

    @Test
    void cannotUseReleaseEndpointOnOrdinaryRoom(){
        Room room=setup();
        room.setIsTestRoom(false);
        assertThatThrownBy(()->action.run(owner,ID,BOT2))
                .isInstanceOf(RuntimeException.class);
        verify(write,never()).deletePlayer(anyString(),anyString());
    }
}
