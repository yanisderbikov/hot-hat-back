package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.TurnStartOutcome;
import ru.hothat.game.api.dto.TurnStartedResponseDTO;
import ru.hothat.game.domain.MatchEngine;
import ru.hothat.game.domain.TurnStarted;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

/**
 * Начать ход.
 *
 * <p>Слово из шляпы тянет сервер. В браузере это делал
 * {@code drawRandomWord(bag)} — то есть шляпу перемешивал и тянул из неё тот
 * же человек, который сейчас будет объяснять. Часы тоже серверные: клиенту
 * оставалось калибровать свои по чужой отметке, и на разъехавшихся часах ход
 * длился по-разному у разных людей.
 */
@Service
@RequiredArgsConstructor
public class BeginTurnUseCase {

    private final MatchStore matchStore;
    private final MatchEngine engine;
    private final MatchViewAssembler views;

    @PreAuthorize("@gameAuthz.isExplainer(#roomId)")
    @Transactional
    public TurnStartedResponseDTO run(HotHatUser user, String roomId) {
        MatchSession session = matchStore.open(roomId);
        TurnStarted started = engine.beginTurn(session.state(), user.uid());
        session.commit();
        return new TurnStartedResponseDTO(outcome(started.outcome()),
                views.turn(session.state(), user.uid()),
                views.state(session.state(), user.uid()));
    }

    private static TurnStartOutcome outcome(TurnStarted.Outcome outcome) {
        return switch (outcome) {
            case STARTED -> TurnStartOutcome.STARTED;
            case ALREADY_RUNNING -> TurnStartOutcome.ALREADY_RUNNING;
            case MATCH_FINISHED -> TurnStartOutcome.MATCH_FINISHED;
        };
    }
}
