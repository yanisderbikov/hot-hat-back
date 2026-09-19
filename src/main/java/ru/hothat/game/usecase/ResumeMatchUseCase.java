package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.MatchResumedResponseDTO;
import ru.hothat.game.domain.MatchEngine;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.domain.TurnRules;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

import java.util.List;

/**
 * Снять паузу хозяина.
 *
 * <p>Снятие не всегда возобновляет партию: пока кто-то не вернулся на связь,
 * пауза остаётся и только меняет причину. Иначе хозяин смог бы доиграть ход
 * командой из одного человека.
 */
@Service
@RequiredArgsConstructor
public class ResumeMatchUseCase {

    private final MatchStore matchStore;
    private final MatchEngine engine;
    private final MatchViewAssembler views;

    @PreAuthorize("@gameAuthz.isHost(#roomId)")
    @Transactional
    public MatchResumedResponseDTO run(HotHatUser user, String roomId) {
        MatchSession session = matchStore.open(roomId);
        MatchState state = session.state();
        List<String> stillMissing = List.copyOf(state.getPauseMissingUids());
        boolean resumed = stillMissing.isEmpty();
        if (resumed) {
            engine.resume(state);
        } else {
            engine.pauseForMissing(state, stillMissing, List.copyOf(state.getPauseMissingNames()));
        }
        session.commit();
        return new MatchResumedResponseDTO(resumed, views.pause(state), stillMissing,
                resumed ? TurnRules.deadline(state) : 0);
    }
}
