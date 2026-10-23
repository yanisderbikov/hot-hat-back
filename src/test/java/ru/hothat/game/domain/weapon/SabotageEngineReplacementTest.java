package ru.hothat.game.domain.weapon;

import org.junit.jupiter.api.Test;
import ru.hothat.game.domain.*;
import ru.hothat.support.*;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class SabotageEngineReplacementTest {
    @Test
    void replacementDoesNotConsumeBeforeAcknowledgement() {
        var clock = TestClock.start();
        var random = new PredictableRandom();
        var engine = new MatchEngine(clock, random);
        var state = new MatchState("hat-0123456789abcdef", true, false, false, Set.of(), 60);
        Matches.start(engine, state, List.of("лес", "небо"));
        engine.beginTurn(state, Matches.ANN);
        var attacker = new MatchPlayer(Matches.CAT, "Катя", Matches.BLUE, false);
        attacker.setArsenal(WeaponRegistry.baseArsenal());
        String clipId = "repl_12345678901234567890";
        state.getClips().put(clipId, new ReplacementClip(clipId, Matches.CAT, Matches.ANN,
                "turn_older", state.getGameNumber(), clock.millis(), clock.millis()));
        var result = new SabotageEngine(clock, random).fire(state, attacker,
                new SabotageCommand(WeaponType.REPLACEMENT, Matches.CAT,
                        null, clipId, null, null, null, false));
        assertThat(result.event().clipId()).isEqualTo(clipId);
        assertThat(attacker.ammo("replacement")).isEqualTo(1);
        assertThat(state.getClips()).containsKey(clipId);
    }
}
