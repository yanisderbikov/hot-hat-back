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
import ru.hothat.testbot.usecase.ReleaseTestBotSeatUseCase;

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
 * SCRUM-45: invited people may claim ONLY a manually released bot slot.
 * Opening a link never deletes another occupied seat or adds an eleventh one.
 */
class TestRoomBotReplacementTest {
    private static final String ID="hat-0123456789abcdef";
    private static final String HOST="host-1",GUEST="guest-1";
    private static final String BOT1="testbot-abcdef-1",BOT2="testbot-abcdef-2",BOT3="testbot-abcdef-3";
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
    private RoomTeam secondTeam(){
        return RoomTeam.builder().roomId(ID).teamId("team-2").name("Вторая")
                .memberUids(new ArrayList<>(List.of(BOT2,BOT3))).build();
    }
    private void setup(Room room,boolean botWasReleased){
        when(auth.requireRoomForWrite(ID)).thenReturn(room);
        when(cards.card(GUEST)).thenReturn(Optional.empty());
        when(armory.loadout(GUEST)).thenReturn(List.of());
        when(armory.loadoutCharged(any())).thenAnswer(inv->
                inv.getArgument(0) instanceof List<?> ids && ids.size()==5);
        var players=new ArrayList<RoomPlayer>();
        players.add(RoomPlayer.builder().roomId(ID).uid(HOST).teamId("team-1")
                .lastSeenAt(System.currentTimeMillis()).build());
        players.add(bot(BOT1,"team-1"));
        if(!botWasReleased)players.add(bot(BOT2,"team-2"));
        players.add(bot(BOT3,"team-2"));
        when(read.getPlayers(ID)).thenReturn(players);
        when(read.getTeams(ID)).thenReturn(List.of(secondTeam()));
        var person=RoomPlayer.builder().roomId(ID).uid(GUEST).name("Настоящий")
                .isTestBot(false).build();
        when(seats.seatPlayer(eq(room),eq(GUEST),isNull(),eq(MEMES),any(RoomSeats.Origin.class)))
                .thenReturn(new RoomSeats.Seated(person,true));
    }
    private Room room(boolean test,boolean released){
        var runtime=new LinkedHashMap<String,Object>(Map.of(
                BOT1,Map.of("name","Bot1"),BOT3,Map.of("name","Bot3")));
        var bots=new ArrayList<>(List.of(BOT1,BOT3));
        if(released)runtime.put(ReleaseTestBotSeatUseCase.RESERVED_SLOTS,
                new LinkedHashMap<>(Map.of(BOT2,Map.of("teamId","team-2","memeLoadout",MEMES))));
        else{bots.add(BOT2);runtime.put(BOT2,Map.of("name","Bot2"));}
        return Room.builder().id(ID).createdBy(HOST).phase("setup")
                .isTestRoom(test).isPrivate(false).ranked(false).maxPlayers(4)
                .teamOrder(new ArrayList<>(List.of("team-1","team-2")))
                .testBotIds(bots).testBotRuntime(runtime).build();
    }
    private static HotHatUser guest(){
        HotHatUser user=mock(HotHatUser.class);
        when(user.uid()).thenReturn(GUEST);
        return user;
    }

    @Test
    void manualVacancyIsClaimedInItsOriginalTeamWithoutEvictingAnybody(){
        Room room=room(true,true);setup(room,true);
        var result=useCase.run(guest(),ID,new EnterRoomRequestDTO(false,BOT2));
        assertThat(result.created()).isTrue();
        assertThat(room.getTestBotIds()).containsExactly(BOT1,BOT3);
        assertThat(room.getTestBotRuntime()).doesNotContainKey(ReleaseTestBotSeatUseCase.RESERVED_SLOTS);
        verify(write,never()).deletePlayer(anyString(),anyString());
        verify(write).saveTeam(argThat(t->
                t.getMemberUids().equals(List.of(GUEST,BOT3))));
        verify(write).savePlayer(argThat(p->
                GUEST.equals(p.getUid()) && "team-2".equals(p.getTeamId())));
        verify(write).save(room);
    }

    @Test
    void occupiedBotIsNeverEvictedByOpeningInviteLink(){
        Room room=room(true,false);setup(room,false);
        assertThatThrownBy(()->useCase.run(guest(),ID,new EnterRoomRequestDTO(false,BOT2)))
                .isInstanceOf(RuntimeException.class);
        verify(write,never()).deletePlayer(anyString(),anyString());
        verify(seats,never()).seatPlayer(any(),anyString(),any(),anyList(),any());
    }

    @Test
    void unknownOrAlreadyClaimedReservationCannotBeUsedTwice(){
        Room room=room(true,true);setup(room,true);
        assertThatThrownBy(()->useCase.run(guest(),ID,
                new EnterRoomRequestDTO(false,"testbot-abcdef-9")))
                .isInstanceOf(RuntimeException.class);
        verify(write,never()).saveTeam(any());
        verify(seats,never()).seatPlayer(any(),anyString(),any(),anyList(),any());
    }

    @Test
    void defaultInviteUsesOpenSlotAndKeepsOtherBots(){
        Room room=room(true,true);setup(room,true);
        useCase.run(guest(),ID,new EnterRoomRequestDTO(false,null));
        assertThat(room.getTestBotIds()).containsExactly(BOT1,BOT3);
        verify(write,never()).deletePlayer(anyString(),anyString());
        verify(write).saveTeam(argThat(team->team.getMemberUids().contains(GUEST)));
    }

    @Test
    void normalRoomCannotSkipPersonalLoadout(){
        Room room=room(false,false);setup(room,false);
        when(armory.requireLoadout(GUEST)).thenThrow(new IllegalStateException("loadout required"));
        assertThatThrownBy(()->useCase.run(guest(),ID,
                new EnterRoomRequestDTO(false,BOT2))).isInstanceOf(IllegalStateException.class);
        verify(write,never()).deletePlayer(anyString(),anyString());
    }
}
