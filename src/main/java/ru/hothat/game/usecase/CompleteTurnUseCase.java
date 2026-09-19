package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.CompleteTurnRequestDTO;
import ru.hothat.game.api.dto.TurnCompletedResponseDTO;
import ru.hothat.game.domain.MatchEngine;
import ru.hothat.game.domain.TurnClosing;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

/**
 * Завершить свой ход досрочно.
 *
 * <p>Идемпотентно по {@code turnId}: повтор находит ход уже закрытым и второго
 * перехода не делает. Без этого двойное нажатие закрывало бы и свой ход, и
 * следующий — чужой.
 */
@Service
@RequiredArgsConstructor
public class CompleteTurnUseCase {

    private final MatchStore matchStore;
    private final MatchEngine engine;
    private final MatchViewAssembler views;

    @PreAuthorize("@gameAuthz.isExplainer(#roomId)")
    @Transactional
    public TurnCompletedResponseDTO run(HotHatUser user, String roomId, CompleteTurnRequestDTO request) {
        MatchSession session = matchStore.open(roomId);
        TurnClosing closing = engine.complete(session.state(), request.turnId());
        session.commit();
        return new TurnCompletedResponseDTO(TurnClosings.outcome(closing.outcome()), closing.preliminaryScore(),
                views.appeal(session.state(), user.uid()), views.state(session.state(), user.uid()));
    }
}
