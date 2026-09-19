package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.AdvanceTurnRequestDTO;
import ru.hothat.game.api.dto.NextTurnResponseDTO;
import ru.hothat.game.api.dto.TurnAdvanceOutcome;
import ru.hothat.game.domain.MatchEngine;
import ru.hothat.game.domain.TurnAdvanced;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

/**
 * Передать очередь следующей команде с экрана итогов.
 *
 * <p>Порядок команд считает сервер. В браузере его считал тот, кто нажал
 * кнопку, — а нажимали её обычно все сразу, и два перехода подряд
 * перескакивали через команду. Идемпотентность по {@code turnId} закрывает
 * это ровно так же, как у завершения хода.
 */
@Service
@RequiredArgsConstructor
public class AdvanceTurnUseCase {

    private final MatchStore matchStore;
    private final MatchEngine engine;
    private final MatchViewAssembler views;

    @PreAuthorize("@gameAuthz.isMember(#roomId)")
    @Transactional
    public NextTurnResponseDTO run(HotHatUser user, String roomId, AdvanceTurnRequestDTO request) {
        MatchSession session = matchStore.open(roomId);
        TurnAdvanced advanced = engine.advance(session.state(), request.turnId());
        session.commit();
        return new NextTurnResponseDTO(outcome(advanced.outcome()), advanced.currentTeamId(),
                views.state(session.state(), user.uid()));
    }

    private static TurnAdvanceOutcome outcome(TurnAdvanced.Outcome outcome) {
        return switch (outcome) {
            case ADVANCED -> TurnAdvanceOutcome.ADVANCED;
            case MATCH_FINISHED -> TurnAdvanceOutcome.MATCH_FINISHED;
            case ALREADY_ADVANCED -> TurnAdvanceOutcome.ALREADY_ADVANCED;
        };
    }
}
