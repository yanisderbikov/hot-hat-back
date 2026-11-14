package ru.hothat.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.media.spi.MemeLibraryPort;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.store.RoomRows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regression: removing a test bot deliberately leaves an invite reservation. */
class AiVoiceSeatServiceTest {
    private static final String ROOM = "hat-1234567890abcdef";
    private static final String TEAM = "team-a";
    private static final String HUMAN = "human-1";
    private static final String SCRIPTED = "testbot-abcdef-1";

    private final RoomRows rows = mock(RoomRows.class);
    private final GetterRoom getter = mock(GetterRoom.class);
    private final SaverRoom saver = mock(SaverRoom.class);
    private final MemeLibraryPort memes = mock(MemeLibraryPort.class);
    private final AiVoiceSeatService seats = new AiVoiceSeatService(rows, getter, saver, memes);

    private final HotHatUser host = new HotHatUser(HUMAN, null, null, false, false, false);

    private Room room(boolean testRoom) {
        return Room.builder().id(ROOM).createdBy(HUMAN)
                .ranked(false).isTestRoom(testRoom).phase("setup").maxPlayers(10).build();
    }

    private RoomPlayer player(String uid, boolean testBot) {
        return RoomPlayer.builder().roomId(ROOM).uid(uid).teamId(TEAM)
                .isTestBot(testBot).build();
    }

    private RoomTeam team(String... persistedUids) {
        return RoomTeam.builder().roomId(ROOM).teamId(TEAM)
                .memberUids(new ArrayList<>(List.of(persistedUids))).build();
    }

    private void prepare(Room room, RoomTeam team, List<RoomPlayer> players) {
        when(rows.lock(ROOM)).thenReturn(Optional.of(room));
        when(getter.getTeam(ROOM, TEAM)).thenReturn(Optional.of(team));
        when(getter.getPlayers(ROOM)).thenReturn(players);
        when(memes.catalog(120)).thenReturn(IntStream.range(0, 5)
                .mapToObj(i -> new MemeLibraryPort.PlayableMeme(
                        "meme-" + i, "Test meme", 1000,
                        "https://example.org/meme/" + i, null, null, null))
                .toList());
    }

    @Test
    void addsChosenAiNextToOneScriptedTestBotWithoutDeletingPartner() {
        Room room=room(true);
        room.setTestBotIds(new ArrayList<>(List.of(SCRIPTED)));
        RoomTeam team=team(SCRIPTED);
        prepare(room,team,List.of(player(SCRIPTED,true)));
        Map<String,Object> result=seats.add(host,ROOM,TEAM,"shustrik");
        assertEquals("Шустрик",result.get("name"));
        assertEquals("shustrik",result.get("characterId"));
        assertEquals(List.of(SCRIPTED,result.get("uid")),team.getMemberUids());
        assertTrue(room.getTestBotIds().contains(SCRIPTED));
        verify(saver,never()).deletePlayer(anyString(),anyString());
    }

    @Test
    void replacesOnlyOneOfTwoScriptedTestBotsAndKeepsOther() {
        String second="testbot-abcdef-2";
        Room room=room(true);
        room.setTestBotIds(new ArrayList<>(List.of(SCRIPTED,second)));
        room.setTestBotRuntime(new LinkedHashMap<>(Map.of(
                SCRIPTED,Map.of("ready",true),second,Map.of("ready",true))));
        RoomTeam team=team(SCRIPTED,second);
        prepare(room,team,List.of(player(SCRIPTED,true),player(second,true)));
        Map<String,Object> result=seats.add(host,ROOM,TEAM,"mudrila");
        assertEquals("Мудрила",result.get("name"));
        assertEquals(List.of(second,result.get("uid")),team.getMemberUids());
        assertEquals(List.of(second),room.getTestBotIds());
        assertFalse(room.getTestBotRuntime().containsKey(SCRIPTED));
        assertTrue(room.getTestBotRuntime().containsKey(second));
        verify(saver).deletePlayer(ROOM,SCRIPTED);
        verify(saver,never()).deletePlayer(ROOM,second);
    }

    @Test
    void onlySiteOwnerCanReplaceScriptedTeammateWithSecondAi() {
        String existing="ai-gpt-live-aaaaaaaaaaaaaaaaaa";
        Room room=room(true);
        room.setTestBotIds(new ArrayList<>(List.of(SCRIPTED)));
        room.setTestBotRuntime(new LinkedHashMap<>(Map.of(SCRIPTED,Map.of("ready",true))));
        RoomTeam team=team(existing,SCRIPTED);
        RoomPlayer ai=player(existing,true);ai.setName("Бубнил");
        prepare(room,team,List.of(ai,player(SCRIPTED,true)));
        HotHatUser siteOwner=new HotHatUser("site-owner",null,null,true,true,false);
        assertThrows(ApiException.class,()->seats.add(host,ROOM,TEAM,"plut"));
        Map<String,Object> result=seats.add(siteOwner,ROOM,TEAM,"plut");
        assertEquals(List.of(existing,result.get("uid")),team.getMemberUids());
        assertEquals("Плут",result.get("name"));
        verify(saver).deletePlayer(ROOM,SCRIPTED);
    }

    @Test
    void scriptedSeatNotOnAuthoritativeTestBotListCannotBeReplaced() {
        String second="testbot-abcdef-2";
        Room room=room(true);
        room.setTestBotIds(new ArrayList<>(List.of(SCRIPTED)));
        RoomTeam team=team(second,SCRIPTED);
        prepare(room,team,List.of(player(second,true),player(SCRIPTED,true)));
        assertThrows(ApiException.class,()->seats.add(host,ROOM,TEAM,"bubnil"));
        verify(saver,never()).savePlayer(any());
    }

    @Test
    void siteOwnerCanStageTwoDistinctAiPlayersInAnEmptyTeam() {
        HotHatUser siteOwner=new HotHatUser("site-owner",null,null,true,true,false);
        Room room=room(true);
        RoomTeam team=team();
        prepare(room,team,List.of());

        Map<String,Object> first=seats.add(siteOwner,ROOM,TEAM,"bubnil");
        assertEquals("Бубнил",first.get("name"));
        assertEquals(List.of(first.get("uid")),team.getMemberUids());

        RoomPlayer firstPlayer=player(String.valueOf(first.get("uid")),true);
        firstPlayer.setName("Бубнил");
        when(getter.getPlayers(ROOM)).thenReturn(List.of(firstPlayer));
        Map<String,Object> second=seats.add(siteOwner,ROOM,TEAM,"plut");
        assertEquals("Плут",second.get("name"));
        assertEquals(List.of(first.get("uid"),second.get("uid")),team.getMemberUids());
        verify(saver,times(2)).savePlayer(any());
    }

    @Test
    void roomHostWithAdminRoleStillCannotStageTwoAiPlayers() {
        HotHatUser adminHost=new HotHatUser(HUMAN,null,null,true,false,false);
        prepare(room(false),team(),List.of());
        assertThrows(ApiException.class,()->seats.add(adminHost,ROOM,TEAM,"bubnil"));
        verify(saver,never()).savePlayer(any());
    }

    @Test
    void fiveDifferentTeamsMayHaveOneAiPlayerEach() {
        Room room=room(false);
        RoomTeam team=team(HUMAN);
        List<RoomPlayer> players=new ArrayList<>();
        players.add(player(HUMAN,false));
        prepare(room,team,players);
        for(String persona:List.of("rublisha","bubnil","shustrik","mudrila","plut")){
            Map<String,Object> result=seats.add(host,ROOM,TEAM,persona);
            // Simulate advancing to a different human team after each addition.
            players.add(RoomPlayer.builder().uid(String.valueOf(result.get("uid")))
                    .name(String.valueOf(result.get("name"))).teamId("prior-"+persona)
                    .isTestBot(true).build());
            when(getter.getPlayers(ROOM)).thenReturn(List.copyOf(players));
        }
        assertEquals(5,players.stream().filter(p->p.getUid().startsWith("ai-gpt-live-")).count());
        assertThrows(ApiException.class,()->seats.add(host,ROOM,TEAM,"rublisha"));
    }

    @Test
    void fillsReleasedTestBotSlotWithoutKeepingInviteReservation() {
        Room room = room(true);
        RoomTeam team = team(HUMAN, SCRIPTED);
        // The test bot was removed: only its invitation marker remains in member_uids.
        prepare(room, team, List.of(player(HUMAN, false)));

        Map<String, Object> result = seats.add(host, ROOM, TEAM);

        assertEquals(Boolean.TRUE, result.get("ok"));
        assertEquals(List.of(HUMAN, result.get("uid")), team.getMemberUids());
        assertEquals("waiting_for_agent", result.get("status"));
        verify(saver).saveTeam(team);
        verify(saver).savePlayer(argThat(p -> result.get("uid").equals(p.getUid())));
        verify(saver, never()).deletePlayer(anyString(), anyString());
    }

    @Test
    void repairsMissingHumanInTeamMemberUids() {
        RoomTeam team = team();
        prepare(room(false), team, List.of(player(HUMAN, false)));

        Map<String, Object> result = seats.add(host, ROOM, TEAM);

        assertEquals(List.of(HUMAN, result.get("uid")), team.getMemberUids());
        verify(saver).saveTeam(team);
    }

    @Test
    void replacesPresentScriptedBotAndRemovesItsRuntime() {
        Room room = room(true);
        room.setTestBotIds(new ArrayList<>(List.of(SCRIPTED)));
        room.setTestBotRuntime(new LinkedHashMap<>(Map.of(SCRIPTED, Map.of("ready", true))));
        RoomTeam team = team(HUMAN, SCRIPTED);
        prepare(room, team, List.of(player(HUMAN, false), player(SCRIPTED, true)));

        Map<String, Object> result = seats.add(host, ROOM, TEAM);

        assertEquals(List.of(HUMAN, result.get("uid")), team.getMemberUids());
        assertFalse(room.getTestBotIds().contains(SCRIPTED));
        assertFalse(room.getTestBotRuntime().containsKey(SCRIPTED));
        verify(saver).deletePlayer(ROOM, SCRIPTED);
        verify(saver).saveTeam(team);
    }

    @Test
    void rejectsTwoRealPlayersWithoutChangingTheirSeats() {
        RoomTeam team = team(HUMAN, "human-2");
        prepare(room(false), team, List.of(player(HUMAN, false), player("human-2", false)));

        assertThrows(ApiException.class, () -> seats.add(host, ROOM, TEAM));

        verify(saver, never()).saveTeam(any());
        verify(saver, never()).savePlayer(any());
    }
}
