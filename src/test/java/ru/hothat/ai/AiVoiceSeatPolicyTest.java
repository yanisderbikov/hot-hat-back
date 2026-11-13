package ru.hothat.ai;

import org.junit.jupiter.api.Test;
import ru.hothat.config.ApiException;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AiVoiceSeatPolicyTest {
    private Room room(boolean ranked, boolean test){
        return Room.builder().id("hat-1234567890abcdef").ranked(ranked)
                .isTestRoom(test).phase("setup").build();
    }
    private RoomPlayer player(String id,String team,boolean scripted){
        return RoomPlayer.builder().roomId("hat-1234567890abcdef").uid(id).teamId(team)
                .isTestBot(scripted).build();
    }
    @Test void acceptsSingleRealHumanInClassicAndSabotage(){
        for(String mode:List.of("classic","sabotage")){
            Room r=room(false,false);r.setGameMode(mode);
            assertDoesNotThrow(()->AiVoiceSeatPolicy.requireEligible(r,"team-a",
                    List.of(player("human-1","team-a",false))));
        }
    }
    @Test void acceptsReplacingScriptedTestBotOnlyWithRealHuman(){
        Room r=room(false,true);
        assertDoesNotThrow(()->AiVoiceSeatPolicy.requireEligible(r,"team-a",
                List.of(player("human-1","team-a",false),player("testbot-abcdef-1","team-a",true))));
        assertThrows(ApiException.class,()->AiVoiceSeatPolicy.requireEligible(r,"team-a",
                List.of(player("testbot-abcdef-1","team-a",true))));
    }
    @Test void rejectsRankedAndAIAIAndFullRealPair(){
        assertThrows(ApiException.class,()->AiVoiceSeatPolicy.requireEligible(room(true,false),"team-a",
                List.of(player("human-1","team-a",false))));
        assertThrows(ApiException.class,()->AiVoiceSeatPolicy.requireEligible(room(false,false),"team-a",
                List.of(player("ai-gpt-live-123","team-a",true))));
        assertThrows(ApiException.class,()->AiVoiceSeatPolicy.requireEligible(room(false,false),"team-a",
                List.of(player("human-1","team-a",false),player("human-2","team-a",false))));
    }
    @Test void refusesMidGame(){
        Room r=room(false,false);r.setPhase("active");
        assertThrows(ApiException.class,()->AiVoiceSeatPolicy.requireEligible(r,"team-a",
                List.of(player("human-1","team-a",false))));
    }
}
