package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.GuessWordRequestDTO;
import ru.hothat.game.api.dto.GuessedWordResponseDTO;
import ru.hothat.game.domain.MatchEngine;
import ru.hothat.game.domain.WordResolved;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

/**
 * Засчитать слово.
 *
 * <p>Очки начисляет сервер, и начисляет атомарной прибавкой к строке команды:
 * в браузере это был {@code increment(1)} внутри пакета записей, и повторить
 * его чтением-правкой-записью значило бы терять очки при быстрых нажатиях.
 *
 * <p>Идемпотентно по идентификатору слова: клиент придумывает его сам и
 * повторяет запрос при потере связи с тем же идентификатором. Повтор находит
 * слово уже разобранным и второго очка не даёт.
 */
@Service
@RequiredArgsConstructor
public class GuessWordUseCase {

    private final MatchStore matchStore;
    private final MatchEngine engine;
    private final MatchViewAssembler views;

    @PreAuthorize("@gameAuthz.isExplainer(#roomId)")
    @Transactional
    public GuessedWordResponseDTO run(HotHatUser user, String roomId, String wordId, GuessWordRequestDTO request) {
        MatchSession session = matchStore.open(roomId);
        String teamId = session.state().getCurrentTeamId();
        WordResolved resolved = engine.guess(session.state(), request.turnId(), wordId);
        session.addTeamScore(teamId, resolved.teamScoreDelta());
        session.commit();
        return new GuessedWordResponseDTO(WordActions.outcome(resolved.outcome()), resolved.word(),
                resolved.nextWord(), resolved.turnScore(), resolved.wordsLeft(), resolved.turnClosed(),
                resolved.appealEndsAt());
    }
}
