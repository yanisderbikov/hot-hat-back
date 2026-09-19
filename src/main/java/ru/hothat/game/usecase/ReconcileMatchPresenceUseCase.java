package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.MatchPresenceOutcome;
import ru.hothat.game.api.dto.MatchPresenceResponseDTO;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.port.MatchPresencePort;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Сверить присутствие и отметиться самому.
 *
 * <p>Сверку заказывает один участник — тот, чей клиент взял на себя опрос.
 * Она отвечает на вопрос, которого нет в базе: кто сейчас на видеосвязи.
 * Строка игрока говорит лишь о том, когда он в последний раз о себе напомнил.
 *
 * <p>Поход в LiveKit сделан до открытия транзакции намеренно: это сетевой
 * вызов на сотни миллисекунд, и держать на нём транзакцию, которая правит
 * партию, значило бы блокировать ход всем остальным. Правило слоёв (§7.2)
 * запрещает внешний вызов внутри транзакции ровно по этой причине, поэтому
 * границу здесь ставит {@link TransactionTemplate}, а не аннотация.
 */
@Service
@RequiredArgsConstructor
public class ReconcileMatchPresenceUseCase {

    /** Превью главной страницы подключается к комнате и игроком не является. */
    private static final String PREVIEW_PREFIX = "preview-";

    private final MatchStore matchStore;
    private final MatchPresencePort presence;
    private final MatchPresenceReconciler reconciler;
    private final MatchViewAssembler views;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @PreAuthorize("@gameAuthz.isMember(#roomId)")
    public MatchPresenceResponseDTO run(HotHatUser user, String roomId) {
        MatchState before = matchStore.read(roomId);
        if (!before.isPlayer(user.uid())) {
            throw ApiException.of("PLAYER_NOT_FOUND", 403);
        }
        if (!before.getPhase().pausable()) {
            return apply(user, roomId, null);
        }
        MatchPresencePort.Roster roster = presence.roster(roomId);
        if (!roster.available()) {
            // Молчащий сервер видеосвязи — не то же самое, что пустая комната:
            // по нему нельзя ни ставить паузу, ни засчитывать поражение.
            MatchState state = matchStore.read(roomId);
            return new MatchPresenceResponseDTO(MatchPresenceOutcome.PRESENCE_UNAVAILABLE, views.pause(state),
                    List.copyOf(state.getPauseMissingUids()), 0, null, views.state(state, user.uid()));
        }
        Set<String> connected = new HashSet<>(roster.identities());
        List<String> previews = connected.stream().filter(id -> id.startsWith(PREVIEW_PREFIX)).toList();
        previews.forEach(identity -> {
            connected.remove(identity);
            presence.disconnect(roomId, identity);
        });
        return apply(user, roomId, connected);
    }

    private MatchPresenceResponseDTO apply(HotHatUser user, String roomId, Set<String> connected) {
        return transactions.execute(status -> {
            MatchSession session = matchStore.open(roomId);
            session.findPlayer(user.uid()).ifPresent(player -> player.setLastSeenAt(clock.millis()));
            if (connected == null) {
                session.commit();
                MatchState state = session.state();
                return new MatchPresenceResponseDTO(MatchPresenceOutcome.NOT_LIVE, views.pause(state),
                        List.of(), 0, views.termination(state.getTermination()), views.state(state, user.uid()));
            }
            MatchPresenceReconciler.Result result = reconciler.reconcile(session, connected, false);
            session.commit();
            MatchState state = session.state();
            return new MatchPresenceResponseDTO(outcome(result.outcome()), views.pause(state),
                    result.missing(), result.limitMs(), views.termination(result.termination()),
                    views.state(state, user.uid()));
        });
    }

    private static MatchPresenceOutcome outcome(MatchPresenceReconciler.Outcome outcome) {
        return switch (outcome) {
            case RUNNING -> MatchPresenceOutcome.RUNNING;
            case PAUSED -> MatchPresenceOutcome.PAUSED;
            case RESUMED -> MatchPresenceOutcome.RESUMED;
            case TECHNICALLY_FINISHED -> MatchPresenceOutcome.TECHNICALLY_FINISHED;
            // Комнату этот сценарий не закрывает: ему такого права не дано.
            case ROOM_CLOSED, NOT_LIVE -> MatchPresenceOutcome.NOT_LIVE;
        };
    }
}
