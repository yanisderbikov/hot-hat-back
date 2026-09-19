package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.ArsenalResponseDTO;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

/**
 * Прочитать своё снаряжение.
 *
 * <p>Только своё: содержимое чужой обоймы — это половина тактики, и раньше
 * оно приезжало каждому вместе с подпиской на игроков комнаты. Чужие
 * <i>счётчики</i> зарядов — другое дело: их рисует плитка каждого, и они едут
 * всем полем {@code ammo} снимка партии и кадра канала.
 */
@Service
@RequiredArgsConstructor
public class GetMyArsenalUseCase {

    private final MatchStore matchStore;
    private final MatchViewAssembler views;

    @PreAuthorize("@gameAuthz.isPlayer(#roomId)")
    @Transactional(readOnly = true)
    public ArsenalResponseDTO run(HotHatUser user, String roomId) {
        MatchSession session = matchStore.readSession(roomId);
        return new ArsenalResponseDTO(views.arsenal(session.player(user.uid())),
                views.clipsOf(session.state(), user.uid()));
    }
}
