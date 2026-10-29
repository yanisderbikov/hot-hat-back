package ru.hothat.testbot.usecase;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.service.testbots.impl.TestBotsServiceImpl;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.media.spi.MemeLibraryPort;
import ru.hothat.service.livekit.LiveKitService;
import ru.hothat.service.game.GameService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TestBotOneWordFullMinuteTest {
    private final TestBotsServiceImpl bots = new TestBotsServiceImpl(
            mock(GetterRoom.class),mock(SaverRoom.class),mock(MemeLibraryPort.class),
            mock(LiveKitService.class),mock(GameService.class));
    private final HotHatUser human = new HotHatUser("human", "", "", true, true, false);

    @Test
    void successfulBotGuessDoesNotEndItsSixtySecondTurnEarly() {
        long now = System.currentTimeMillis();
        Room room = new Room();
        room.setPhase("active");
        room.setCurrentTeamId("bots-team");
        room.setTeamRosters(Map.of("bots-team",List.of("bot1","bot2")));
        room.setTestBotIds(List.of("bot1","bot2"));
        room.setTurnStartedAt(Instant.ofEpochMilli(now - 8000));
        room.setTurnDurationSeconds(60.0);
        room.setCurrentTurnScore(1);
        room.setCurrentWord(null);
        room.setBag(List.of("неиспользованное слово"));
        Map<?,?> result = ReflectionTestUtils.invokeMethod(bots,"playActiveTurn",room,human,now);
        assertEquals("active",room.getPhase());
        assertEquals("bot_one_word_waiting_for_full_turn",result.get("action"));
        assertEquals(1,room.getCurrentTurnScore());
        assertEquals(List.of("неиспользованное слово"),room.getBag());
    }

    @Test
    void teammateBotCannotGuessOrSkipDuringOwnersTurn() {
        long now = System.currentTimeMillis();
        Room room = new Room();
        room.setPhase("active");
        room.setCurrentTeamId("owner-team");
        room.setTeamRosters(Map.of("owner-team",List.of("human","bot1")));
        room.setTestBotIds(List.of("bot1"));
        room.setTurnStartedAt(Instant.ofEpochMilli(now - 8000));
        room.setTurnDurationSeconds(60.0);
        room.setCurrentTurnScore(0);
        room.setCurrentWord("слово");
        room.setBag(List.of("следующее слово"));
        Map<?,?> result=ReflectionTestUtils.invokeMethod(bots,"playActiveTurn",room,human,now);
        assertEquals("owner_controls_words",result.get("action"));
        assertEquals("слово",room.getCurrentWord());
        assertEquals(0,room.getCurrentTurnScore());
        assertEquals(List.of("следующее слово"),room.getBag());
    }
}
