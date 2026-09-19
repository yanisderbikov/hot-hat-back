package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.DisconnectionOutcome;
import ru.hothat.game.api.dto.MatchDisconnectionResponseDTO;
import ru.hothat.game.api.dto.PauseView;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.port.MatchPresencePort;
import ru.hothat.game.port.RoomLifecyclePort;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Доложить о разрыве связи.
 *
 * <p>Единственная ветка, которой позволено закрыть комнату: если из обычной
 * партии исчезли разом все, доигрывать её некому, а комната осталась бы
 * висеть до уборщика. У рейтинговой партии такого исхода нет — там результат
 * нужен рейтингу, и партия заканчивается техническим завершением.
 *
 * <p>Отметка ушедшего гасится до опроса LiveKit, а не после: его список
 * отдаёт ушедшего ещё несколько секунд, и запоздалая проверка сняла бы паузу.
 */
@Service
@RequiredArgsConstructor
public class ReportDisconnectionUseCase {

    private static final String PREVIEW_PREFIX = "preview-";

    private final MatchStore matchStore;
    private final MatchPresencePort presence;
    private final MatchPresenceReconciler reconciler;
    private final MatchViewAssembler views;
    private final RoomLifecyclePort rooms;
    private final TransactionTemplate transactions;

    @PreAuthorize("@gameAuthz.isMember(#roomId)")
    public MatchDisconnectionResponseDTO run(HotHatUser user, String roomId) {
        MatchState before = matchStore.read(roomId);
        if (!before.isPlayer(user.uid())) {
            throw ApiException.of("PLAYER_NOT_FOUND", 403);
        }
        transactions.executeWithoutResult(status -> rooms.dropSeatPresence(roomId, user.uid()));

        MatchPresencePort.Roster roster = presence.roster(roomId);
        Set<String> connected = new HashSet<>(roster.identities());
        // Доложивший ушёл, что бы ни отдавал список: он сам об этом сказал.
        connected.remove(user.uid());
        if (roster.available()) {
            List<String> previews = connected.stream().filter(id -> id.startsWith(PREVIEW_PREFIX)).toList();
            previews.forEach(identity -> {
                connected.remove(identity);
                presence.disconnect(roomId, identity);
            });
        }

        return transactions.execute(status -> {
            MatchSession session = matchStore.open(roomId);
            // Комнату закрываем только тогда, когда список участников удалось
            // получить: по молчащей видеосвязи «ушли все» неотличимо от «связь легла».
            MatchPresenceReconciler.Result result = reconciler.reconcile(session, connected, roster.available());
            if (result.outcome() == MatchPresenceReconciler.Outcome.ROOM_CLOSED) {
                return new MatchDisconnectionResponseDTO(DisconnectionOutcome.ROOM_CLOSED,
                        PauseView.running(), result.missing(), null, null);
            }
            session.commit();
            MatchState state = session.state();
            return new MatchDisconnectionResponseDTO(outcome(result.outcome()), views.pause(state),
                    result.missing(), views.termination(result.termination()), views.state(state, user.uid()));
        });
    }

    private static DisconnectionOutcome outcome(MatchPresenceReconciler.Outcome outcome) {
        return switch (outcome) {
            case PAUSED -> DisconnectionOutcome.PAUSED;
            case TECHNICALLY_FINISHED -> DisconnectionOutcome.TECHNICALLY_FINISHED;
            case ROOM_CLOSED -> DisconnectionOutcome.ROOM_CLOSED;
            case RUNNING, RESUMED, NOT_LIVE -> DisconnectionOutcome.RUNNING;
        };
    }
}
