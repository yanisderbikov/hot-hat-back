package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.ReplaceLoadoutSlotRequestDTO;
import ru.hothat.game.api.dto.ReplacedLoadoutSlotResponseDTO;
import ru.hothat.game.domain.LoadoutRules;
import ru.hothat.game.domain.MatchPlayer;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.port.MemeCatalogPort;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

import java.util.List;

/**
 * Заменить мем в одном слоте посреди партии.
 *
 * <p>Менять можно в любой момент, кроме собственного хода: активная команда
 * обойму не трогает — иначе она подбирала бы ролик под то, что происходит на
 * сцене прямо сейчас.
 *
 * <p>Новый мем встаёт на место старого во всех трёх очередях сразу — в
 * доступных, в резерве и в переработке. Иначе замена выдала бы лишний
 * заряд: отстрелянный ролик заменился бы свежим и снова стал доступен.
 */
@Service
@RequiredArgsConstructor
public class ReplaceLoadoutSlotUseCase {

    private final MatchStore matchStore;
    private final MemeCatalogPort memes;
    private final MatchViewAssembler views;

    @PreAuthorize("@gameAuthz.isPlayer(#roomId)")
    @Transactional
    public ReplacedLoadoutSlotResponseDTO run(HotHatUser user, String roomId, int slotIndex,
                                              ReplaceLoadoutSlotRequestDTO request) {
        if (slotIndex < 0 || slotIndex >= LoadoutRules.SIZE) {
            throw ApiException.of("MEME_SLOT_INVALID", 400);
        }
        String memeId = request.memeId().trim();
        if (!memes.exists(memeId)) {
            throw ApiException.of("MEME_NOT_LOADED", 404);
        }
        MatchSession session = matchStore.open(roomId);
        MatchState state = session.state();
        if (state.activeRoster().contains(user.uid())) {
            throw ApiException.of("ACTIVE_TEAM_LOADOUT_LOCKED", 409);
        }
        MatchPlayer player = session.player(user.uid());
        List<String> loadout = LoadoutRules.normalize(player.getLoadout());
        if (loadout.size() != LoadoutRules.SIZE) {
            throw ApiException.of("LOADOUT_REQUIRED", 409);
        }
        String previous = loadout.get(slotIndex);
        if (previous.equals(memeId)) {
            // Повтор той же замены — не ошибка: человек добился желаемого.
            return new ReplacedLoadoutSlotResponseDTO(false, slotIndex, previous, memeId, views.arsenal(player));
        }
        if (loadout.contains(memeId)) {
            throw ApiException.of("MEME_DUPLICATE", 409);
        }
        LoadoutRules.replaceSlot(player, slotIndex, memeId);
        session.commit();
        return new ReplacedLoadoutSlotResponseDTO(true, slotIndex, previous, memeId, views.arsenal(player));
    }
}
