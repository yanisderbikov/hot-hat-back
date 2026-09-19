package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.domain.weapon.ReplacementClipRules;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

/**
 * Отказаться от своего клипа.
 *
 * <p>Слот освобождается сразу — снятый клип может стать бесполезен ещё до
 * применения: подменяемый игрок уже отыграл, и ждать его следующего хода
 * дольше, чем снять новый.
 *
 * <p>Чужой клип стереть нельзя: авторство — инвариант этого сценария.
 */
@Service
@RequiredArgsConstructor
public class DiscardReplacementClipUseCase {

    private final MatchStore matchStore;

    @PreAuthorize("@gameAuthz.isPlayer(#roomId)")
    @Transactional
    public void run(HotHatUser user, String roomId, String clipId) {
        MatchSession session = matchStore.open(roomId);
        ReplacementClipRules.discard(session.state(), clipId, user.uid());
        session.commit();
    }
}
