package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.ArsenalView;
import ru.hothat.game.api.dto.PlayerAmmoView;
import ru.hothat.game.api.dto.MatchStateResponseDTO;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

import java.util.List;

/**
 * Прочитать состояние партии.
 *
 * <p>Один композитный ответ вместо документа комнаты и трёх подписок. Слово
 * текущего хода видит только объясняющий — это правило исполняет сборщик
 * проекций, и обойти его чтением нельзя.
 *
 * <p>Состав читается одним запросом до поиска своего места: {@code players()}
 * кладёт всех в сессию, и {@code findPlayer} за своим уже не ходит в базу.
 * Обратный порядок стоил бы второго запроса на каждый снимок.
 */
@Service
@RequiredArgsConstructor
public class GetMatchStateUseCase {

    private final MatchStore matchStore;
    private final MatchViewAssembler views;

    @PreAuthorize("@gameAuthz.isMemberOrSpectator(#roomId)")
    @Transactional(readOnly = true)
    public MatchStateResponseDTO run(HotHatUser user, String roomId) {
        MatchSession session = matchStore.readSession(roomId);
        List<PlayerAmmoView> ammo = views.ammo(session.players());
        ArsenalView arsenal = session.findPlayer(user.uid()).map(views::arsenal).orElse(null);
        return new MatchStateResponseDTO(views.state(session.state(), user.uid()), arsenal, ammo,
                views.clipsOf(session.state(), user.uid()));
    }
}
