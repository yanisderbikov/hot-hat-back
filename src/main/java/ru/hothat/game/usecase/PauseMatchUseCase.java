package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.MatchPausedResponseDTO;
import ru.hothat.game.domain.MatchEngine;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

/**
 * Поставить партию на паузу.
 *
 * <p>Остаток хода замораживается числом, а не остановкой часов: часы у всех
 * свои, а остаток — общий. После снятия паузы ход продолжится ровно с него.
 */
@Service
@RequiredArgsConstructor
public class PauseMatchUseCase {

    private final MatchStore matchStore;
    private final MatchEngine engine;
    private final MatchViewAssembler views;

    @PreAuthorize("@gameAuthz.isHost(#roomId)")
    @Transactional
    public MatchPausedResponseDTO run(HotHatUser user, String roomId) {
        MatchSession session = matchStore.open(roomId);
        engine.pauseByHost(session.state());
        session.commit();
        return new MatchPausedResponseDTO(views.pause(session.state()),
                session.state().getPausedTurnRemainingMs(),
                session.state().getPausedAppealRemainingMs());
    }
}
