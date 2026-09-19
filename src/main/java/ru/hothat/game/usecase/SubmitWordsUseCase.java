package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.SubmitWordsRequestDTO;
import ru.hothat.game.api.dto.SubmittedWordsResponseDTO;
import ru.hothat.game.domain.MatchPhase;
import ru.hothat.game.port.WordSubmissionPort;
import ru.hothat.game.store.MatchStore;

/**
 * Сдать слова в шляпу.
 *
 * <p>Только до начала партии: слово, доложенное в идущую партию, попало бы в
 * шляпу мимо перемешивания и выпало бы предсказуемо.
 *
 * <p>Чужие слова этот сценарий не читает и не отдаёт — только свои и общий
 * счётчик.
 */
@Service
@RequiredArgsConstructor
public class SubmitWordsUseCase {

    private final MatchStore matchStore;
    private final WordSubmissionPort wordSubmissions;

    @PreAuthorize("@gameAuthz.isMember(#roomId)")
    @Transactional
    public SubmittedWordsResponseDTO run(HotHatUser user, String roomId, SubmitWordsRequestDTO request) {
        if (matchStore.read(roomId).getPhase() != MatchPhase.SETUP) {
            throw ApiException.of("WORDS_LOCKED", 409);
        }
        WordSubmissionPort.SubmissionView saved =
                wordSubmissions.write(roomId, user.uid(), request.words(), false);
        return new SubmittedWordsResponseDTO(saved.words(), saved.mine(), saved.total());
    }
}
