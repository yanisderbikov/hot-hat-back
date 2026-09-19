package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.AppealVotesResponseDTO;
import ru.hothat.game.api.dto.CastAppealVoteRequestDTO;
import ru.hothat.game.domain.AppealVoteCast;
import ru.hothat.game.domain.MatchEngine;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

/**
 * Подать голос за отмену слова.
 *
 * <p>«Голосует не своя команда» — правило партии, а не право доступа, поэтому
 * оно живёт здесь, в движке, а не в предикате: разбираемая команда меняется
 * от хода к ходу, и предикат уровня класса пересчитывал бы её на каждый
 * запрос.
 */
@Service
@RequiredArgsConstructor
public class CastAppealVoteUseCase {

    private final MatchStore matchStore;
    private final MatchEngine engine;
    private final MatchViewAssembler views;

    @PreAuthorize("@gameAuthz.isPlayer(#roomId)")
    @Transactional
    public AppealVotesResponseDTO run(HotHatUser user, String roomId, String wordId,
                                      CastAppealVoteRequestDTO request) {
        MatchSession session = matchStore.open(roomId);
        AppealVoteCast cast = engine.vote(session.state(), user.uid(), wordId, request.cancelWord());
        session.commit();
        return new AppealVotesResponseDTO(cast.myVotes(), cast.votesForWord(),
                views.appeal(session.state(), user.uid()));
    }
}
