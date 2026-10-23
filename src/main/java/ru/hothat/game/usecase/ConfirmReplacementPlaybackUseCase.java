package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.ConfirmReplacementPlaybackRequestDTO;
import ru.hothat.game.api.dto.ConfirmedReplacementPlaybackResponseDTO;
import ru.hothat.game.domain.ReplacementClip;
import ru.hothat.game.domain.SabotageEvent;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

import java.time.Clock;

/**
 * SCRUM-22: the target is the only witness of a playable replacement stream.
 * Retain clips and ammunition after a failed/disconnected playback attempt.
 * The room transaction makes duplicate success confirmations idempotent.
 */
@Service
@RequiredArgsConstructor
public class ConfirmReplacementPlaybackUseCase {
    private final MatchStore matchStore;
    private final Clock clock;

    @PreAuthorize("@gameAuthz.isPlayer(#roomId)")
    @Transactional
    public ConfirmedReplacementPlaybackResponseDTO run(HotHatUser user, String roomId, String clipId,
                                                      ConfirmReplacementPlaybackRequestDTO request) {
        MatchSession session = matchStore.open(roomId);
        var state = session.state();
        ReplacementClip clip = state.getClips().get(clipId);
        if (clip == null) {
            // Already settled: a second ACK must never burn a second charge.
            return new ConfirmedReplacementPlaybackResponseDTO(false, false);
        }
        if (!clip.ready() || !clip.targetUid().equals(user.uid()) || clip.gameNumber() != state.getGameNumber()) {
            throw ApiException.of("REPLACEMENT_CLIP_INVALID", 404);
        }
        long now = clock.millis();
        SabotageEvent event = state.getRecentEvents().stream()
                .filter(e -> "replacement".equals(e.type()) && request.eventId().equals(e.id())
                        && clipId.equals(e.clipId()) && user.uid().equals(e.targetUid())
                        && clip.attackerUid().equals(e.attackerUid())
                        && e.gameNumber() == state.getGameNumber())
                .findFirst().orElseThrow(() -> ApiException.of("REPLACEMENT_EVENT_INVALID", 409));
        if (now < event.createdAtMs() || now - event.createdAtMs() > 15_000) {
            throw ApiException.of("REPLACEMENT_ACK_EXPIRED", 409);
        }
        if (!request.played()) {
            // No charge; allow the same clip to be used on a later turn.
            state.getLocks().setReplacementUntil(0);
            session.commit();
            return new ConfirmedReplacementPlaybackResponseDTO(false, false);
        }
        // Spend and removal are part of one transactional room update.
        session.player(clip.attackerUid()).spend("replacement", 1);
        state.getClips().remove(clipId);
        session.commit();
        return new ConfirmedReplacementPlaybackResponseDTO(true, true);
    }
}
