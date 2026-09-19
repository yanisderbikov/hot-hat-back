package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.MatchDepartureResponseDTO;
import ru.hothat.game.domain.MatchEngine;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.port.RoomLifecyclePort;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Сообщить, что уходишь.
 *
 * <p>Приходит на закрытии вкладки и на разрыве соединения с видеосвязью, когда
 * браузер даёт одну попытку и ответа уже не прочитает. Поэтому здесь нет
 * похода в LiveKit: его список отдаёт ушедшего ещё несколько секунд, а
 * отметку надо погасить сейчас — иначе следующая сверка сочтёт человека на
 * месте и снимет паузу зря.
 *
 * <p>Комнату этот сценарий не закрывает никогда: уход одного человека не повод
 * стирать чужую партию.
 */
@Service
@RequiredArgsConstructor
public class ReportDepartureUseCase {

    private final MatchStore matchStore;
    private final MatchEngine engine;
    private final MatchViewAssembler views;
    private final RoomLifecyclePort rooms;

    @PreAuthorize("@gameAuthz.isMember(#roomId)")
    @Transactional
    public MatchDepartureResponseDTO run(HotHatUser user, String roomId) {
        rooms.dropSeatPresence(roomId, user.uid());
        MatchSession session = matchStore.open(roomId);
        MatchState state = session.state();
        if (!state.getPhase().pausable() || !state.isPlayer(user.uid())) {
            // Ушедший не играет в этой партии либо партия не идёт: гасить
            // отметку присутствия — всё, что от нас требовалось.
            return new MatchDepartureResponseDTO(false, views.pause(state));
        }
        List<String> missing = new ArrayList<>(state.getPauseMissingUids());
        if (!missing.contains(user.uid())) {
            missing.add(user.uid());
        }
        engine.pauseForMissing(state, missing, missing.stream().map(state::nameOf).toList());
        session.commit();
        return new MatchDepartureResponseDTO(true, views.pause(state));
    }
}
