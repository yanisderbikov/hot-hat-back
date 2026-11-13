package ru.hothat.game.usecase;

import org.junit.jupiter.api.Test;
import ru.hothat.game.domain.MatchState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchPresenceExpectedRosterTest {
    private MatchState state(boolean testRoom, Set<String> scriptedBots, Map<String, List<String>> teams) {
        MatchState match = new MatchState("hat-1234567890abcdef", false, testRoom,
                false, scriptedBots, 60);
        match.setRosters(new LinkedHashMap<>(teams));
        return match;
    }

    @Test
    void aiVirtualSeatNeverBlocksARealHumanAtMatchStart() {
        Map<String, List<String>> teams = new LinkedHashMap<>();
        teams.put("a", List.of("human-1", "ai-gpt-live-1234567890abcdef12"));
        teams.put("b", List.of("human-2", "human-3"));
        MatchState match = state(false, Set.of(), teams);
        assertEquals(List.of("human-1", "human-2", "human-3"),
                MatchPresenceReconciler.expectedTransportParticipants(match));
    }

    @Test
    void scriptedTestBotsAndAiSeatsAreExcludedButHumansStillRequired() {
        Map<String, List<String>> teams = new LinkedHashMap<>();
        teams.put("a", List.of("human-1", "ai-gpt-live-1234567890abcdef12"));
        teams.put("b", List.of("human-2", "testbot-abcdef-1"));
        MatchState match = state(true, Set.of("testbot-abcdef-1"), teams);
        assertEquals(List.of("human-1", "human-2"),
                MatchPresenceReconciler.expectedTransportParticipants(match));
    }

    @Test
    void normalPlayerRosterRemainsUnchanged() {
        Map<String, List<String>> teams = new LinkedHashMap<>();
        teams.put("a", List.of("human-1", "human-2"));
        MatchState match = state(false, Set.of(), teams);
        assertEquals(List.of("human-1", "human-2"),
                MatchPresenceReconciler.expectedTransportParticipants(match));
    }
}
