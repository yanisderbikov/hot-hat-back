package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.ExpireTurnRequestDTO;
import ru.hothat.game.api.dto.TurnExpiredResponseDTO;
import ru.hothat.game.domain.MatchEngine;
import ru.hothat.game.domain.TurnClosing;
import ru.hothat.game.domain.TurnRules;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

import java.time.Clock;

/**
 * Закрыть истёкший ход.
 *
 * <p>Право — у любого участника комнаты, а не только у объясняющего: у него
 * мог закрыться браузер ровно на последней секунде, и партия зависла бы
 * навсегда. Опасности в этом нет — время считает сервер, и до дедлайна ответ
 * будет {@code NOT_YET}, сколько бы раз ни попросили.
 *
 * <p>Тот же сценарий вытаскивает партию из состояния «шляпа пуста, а ход
 * открыт»: в браузере для этого была отдельная процедура-спасатель
 * {@code ensureExhaustedTurnAdvanced()}.
 */
@Service
@RequiredArgsConstructor
public class ExpireTurnUseCase {

    private final MatchStore matchStore;
    private final MatchEngine engine;
    private final MatchViewAssembler views;
    private final Clock clock;

    @PreAuthorize("@gameAuthz.isMember(#roomId)")
    @Transactional
    public TurnExpiredResponseDTO run(HotHatUser user, String roomId, ExpireTurnRequestDTO request) {
        MatchSession session = matchStore.open(roomId);
        TurnClosing closing = engine.expire(session.state(), request.turnId());
        long remaining = closing.outcome() == TurnClosing.Outcome.NOT_YET
                ? TurnRules.remainingMs(session.state(), clock.millis()) : 0;
        session.commit();
        return new TurnExpiredResponseDTO(TurnClosings.outcome(closing.outcome()), remaining,
                views.appeal(session.state(), user.uid()), views.state(session.state(), user.uid()));
    }
}
