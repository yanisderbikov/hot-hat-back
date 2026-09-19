package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.SkipWordRequestDTO;
import ru.hothat.game.api.dto.SkippedWordResponseDTO;
import ru.hothat.game.domain.MatchEngine;
import ru.hothat.game.domain.WordResolved;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

/**
 * Пропустить слово.
 *
 * <p>Слово возвращается в шляпу и может выпасть снова — в этом же ходу или в
 * чужом. Пропуск, в отличие от засчитывания, ход не закрывает даже последним
 * словом: закрывает его время либо пустая шляпа.
 */
@Service
@RequiredArgsConstructor
public class SkipWordUseCase {

    private final MatchStore matchStore;
    private final MatchEngine engine;

    @PreAuthorize("@gameAuthz.isExplainer(#roomId)")
    @Transactional
    public SkippedWordResponseDTO run(HotHatUser user, String roomId, String wordId, SkipWordRequestDTO request) {
        MatchSession session = matchStore.open(roomId);
        WordResolved resolved = engine.skip(session.state(), request.turnId(), wordId);
        session.commit();
        return new SkippedWordResponseDTO(WordActions.outcome(resolved.outcome()), resolved.word(),
                resolved.nextWord(), resolved.turnScore(), resolved.wordsLeft(), resolved.turnClosed(),
                resolved.appealEndsAt());
    }
}
