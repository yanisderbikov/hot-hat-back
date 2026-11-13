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
