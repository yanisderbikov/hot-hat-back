package ru.hothat.game.usecase;

import org.junit.jupiter.api.Test;
import ru.hothat.config.HotHatUser;
import ru.hothat.config.ApiException;
import ru.hothat.game.api.dto.ConfirmReplacementPlaybackRequestDTO;
import ru.hothat.game.domain.MatchPlayer;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.domain.ReplacementClip;
import ru.hothat.game.domain.SabotageEvent;
import ru.hothat.game.domain.WeaponRegistry;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;
import ru.hothat.support.TestClock;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ConfirmReplacementPlaybackUseCaseTest {
    static final String CLIP = "repl_12345678901234567890";
    static final String EVENT = "sab_12345678901234567890";
    static final String TARGET = "target-uid";
    static final String ATTACKER = "attacker-uid";

    private Fixture fixture() {
        var clock = TestClock.start();
        MatchStore store = mock(MatchStore.class);
        MatchSession session = mock(MatchSession.class);
        MatchState state = new MatchState("hat-0123456789abcdef", true, false, false, Set.of(), 60);
        state.setGameNumber(1);
        state.getClips().put(CLIP, new ReplacementClip(CLIP, ATTACKER, TARGET,
                "turn_prior", 1, clock.millis() - 10000, clock.millis() - 1000));
        state.setRecentEvents(List.of(new SabotageEvent(EVENT, "replacement",
                null, CLIP, "turn_prior", ATTACKER, "Attacker", TARGET,
                clock.millis() - 1000, 10000, 1,
                null, null, null, null, null, null, null, null, null, null)));
        MatchPlayer attacker = new MatchPlayer(ATTACKER, "Attacker", "blue", false);
        attacker.setArsenal(WeaponRegistry.baseArsenal());
        when(store.open(state.getRoomId())).thenReturn(session);
        when(session.state()).thenReturn(state);
        when(session.player(ATTACKER)).thenReturn(attacker);
        return new Fixture(state, session, attacker,
                new ConfirmReplacementPlaybackUseCase(store, clock));
    }

    private record Fixture(MatchState state, MatchSession session, MatchPlayer attacker,
                           ConfirmReplacementPlaybackUseCase useCase) {}

    private static HotHatUser target() {
        return new HotHatUser(TARGET, "", "", false, false, false);
    }

    @Test
    void failedPlaybackKeepsClipAndCharge() {
        Fixture f = fixture();
        var answer = f.useCase.run(target(), f.state.getRoomId(), CLIP,
                new ConfirmReplacementPlaybackRequestDTO(EVENT, false));
        assertThat(answer.charged()).isFalse();
        assertThat(f.attacker.ammo("replacement")).isEqualTo(1);
        assertThat(f.state.getClips()).containsKey(CLIP);
        verify(f.session, never()).commit();
    }

    @Test
    void successfulPlaybackConsumesExactlyOnce() {
        Fixture f = fixture();
        var request = new ConfirmReplacementPlaybackRequestDTO(EVENT, true);
        assertThat(f.useCase.run(target(), f.state.getRoomId(), CLIP, request).charged()).isTrue();
        assertThat(f.attacker.ammo("replacement")).isZero();
        assertThat(f.state.getClips()).doesNotContainKey(CLIP);
        assertThat(f.useCase.run(target(), f.state.getRoomId(), CLIP, request).charged()).isFalse();
        assertThat(f.attacker.ammo("replacement")).isZero();
        verify(f.session, times(1)).commit();
    }

    @Test
    void foreignPlaybackCannotConsumeClip() {
        Fixture f = fixture();
        var intruder = new HotHatUser("another-player", "", "", false, false, false);
        assertThatThrownBy(() -> f.useCase.run(intruder, f.state.getRoomId(), CLIP,
                new ConfirmReplacementPlaybackRequestDTO(EVENT, true)))
                .isInstanceOf(ApiException.class);
        assertThat(f.attacker.ammo("replacement")).isEqualTo(1);
    }
}
