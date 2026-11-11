package ru.hothat.room.usecase;

import org.junit.jupiter.api.Test;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.EnterRoomRequestDTO;
import ru.hothat.sabotage.spi.SabotageArmoryPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SCRUM-44: the test bot seat is atomically transferred to the incoming human.
 * No admin privileges, ranking/division bypass for ordinary games, or paid
 * GenAPI calls. These tests exercise the actual production use-case.
 */
class TestRoomBotReplacementTest {
    private static final String ID="hat-0123456789abcdef";
    private static final String HOST="host-1";
    private static final String GUEST="guest-1";
    private static final String BOT1="testbot-abcdef-1";
    private static final String BOT2="testbot-abcdef-2";
    private static final String BOT3="testbot-abcdef-3";
    private static final List<String> MEMES=List.of("m1","m2","m3","m4","m5");

    private final RoomAccessGuard auth=mock(RoomAccessGuard.class);
    private final GetterRoom read=mock(GetterRoom.class);
    private final SaverRoom write=mock(SaverRoom.class);
    private final SabotageArmoryPort armory=mock(SabotageArmoryPort.class);
    private final PlayerCardPort cards=mock(PlayerCardPort.class);
    private final RoomSeats seats=mock(RoomSeats.class);
    private final RoomProjections projections=mock(RoomProjections.class);
    private final EnterRoomUseCase useCase=
            new EnterRoomUseCase(auth,read,write,armory,cards,seats,projections);

    private RoomPlayer bot(String uid, String team){
        return RoomPlayer.builder().roomId(ID).uid(uid).name("Тест-бот")
                .isTestBot(true).teamId(team).memeLoadout(MEMES)
                .lastSeenAt(System.currentTimeMillis()).build();
    }

    private void setup(Room room){
        when(auth.requireRoomForWrite(ID)).thenReturn(room);
        when(cards.card(GUEST)).thenReturn(Optional.empty());
        when(armory.loadout(GUEST)).thenReturn(List.of());
        when(armory.loadoutCharged(any())).thenAnswer(inv->
                inv.getArgument(0) instanceof List<?> ids && ids.size()==5);
        when(read.getPlayers(ID)).thenReturn(List.of(
                RoomPlayer.builder().roomId(ID).uid(HOST).teamId("team-1")
                        .lastSeenAt(System.currentTimeMillis()).build(),
                bot(BOT1,"team-1"),bot(BOT2,"team-2"),bot(BOT3,"team-2")));
        when(read.getTeam(ID,"team-2")).thenReturn(Optional.of(
                RoomTeam.builder().roomId(ID).teamId("team-2")
                        .name("Вторая").memberUids(new ArrayList<>(List.of(BOT2,BOT3))).build()));
        RoomPlayer player=RoomPlayer.builder().roomId(ID).uid(GUEST).name("Настоящий")
                .isTestBot(false).build();
        when(seats.seatPlayer(eq(room),eq(GUEST),isNull(),eq(MEMES),any(RoomSeats.Origin.class)))
                .thenReturn(new RoomSeats.Seated(player,true));
    }

    private Room room(boolean test){
        return Room.builder().id(ID).createdBy(HOST).phase("setup")
                .isTestRoom(test).isPrivate(false).ranked(false).maxPlayers(4)
                .testBotIds(new ArrayList<>(List.of(BOT1,BOT2,BOT3)))
                .testBotRuntime(new LinkedHashMap<>(Map.of(
                        BOT1,Map.of("name","Bot1"),BOT2,Map.of("name","Bot2"),
                        BOT3,Map.of("name","Bot3")))).build();
    }

    @Test
    void specificBotIsReplacedWithoutChangingOtherSeatsOrPayingForGeneration(){
        Room room=room(true);setup(room);
        HotHatUser guest=mock(HotHatUser.class);
        when(guest.uid()).thenReturn(GUEST);
        var result=useCase.run(guest,ID,new EnterRoomRequestDTO(false,BOT2));

        assertThat(result.created()).isTrue();
        assertThat(room.getTestBotIds()).containsExactly(BOT1,BOT3);
        assertThat(room.getTestBotRuntime()).doesNotContainKey(BOT2);
        verify(write).deletePlayer(ID,BOT2);
        verify(read).getTeam(ID,"team-2");
        verify(write).saveTeam(argThat(t->
                t.getMemberUids().equals(List.of(GUEST,BOT3))));
        verify(write).savePlayer(argThat(p->
                GUEST.equals(p.getUid()) && "team-2".equals(p.getTeamId())));
        verify(write).save(room);
        verifyNoMoreInteractions(projections);
    }

    @Test
    void claimedOrForgedBotUidCannotRemoveAnyRealPlayer(){
        Room room=room(true);setup(room);
        HotHatUser guest=mock(HotHatUser.class);
        when(guest.uid()).thenReturn(GUEST);
        assertThatThrownBy(()->useCase.run(guest,ID,
                new EnterRoomRequestDTO(false,"someone-else"))).isInstanceOf(RuntimeException.class);
        verify(write,never()).deletePlayer(anyString(),anyString());
        verify(seats,never()).seatPlayer(any(),anyString(),any(),anyList(),any());
    }

    @Test
    void normalRoomCannotClaimBotSeatOrSkipPersonalLoadout(){
        Room room=room(false);setup(room);
        HotHatUser guest=mock(HotHatUser.class);
        when(guest.uid()).thenReturn(GUEST);
        when(armory.requireLoadout(GUEST)).thenThrow(new IllegalStateException("loadout required"));
        assertThatThrownBy(()->useCase.run(guest,ID,
                new EnterRoomRequestDTO(false,BOT2))).isInstanceOf(IllegalStateException.class);
        verify(write,never()).deletePlayer(anyString(),anyString());
    }
}
