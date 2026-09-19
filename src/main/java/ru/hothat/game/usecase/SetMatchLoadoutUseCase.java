package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.MatchLoadoutResponseDTO;
import ru.hothat.game.api.dto.SetMatchLoadoutRequestDTO;
import ru.hothat.game.domain.LoadoutRules;
import ru.hothat.game.domain.MatchPhase;
import ru.hothat.game.domain.MatchPlayer;
import ru.hothat.game.port.MemeCatalogPort;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

import java.util.List;

/**
 * Зарядить обойму мемов на партию.
 *
 * <p>Существование роликов проверяет сервер. Раньше клиент отправлял обойму
 * как есть, и удалённый из библиотеки мем всплывал уже выстрелом — посреди
 * чужого хода, когда исправлять поздно.
 */
@Service
@RequiredArgsConstructor
public class SetMatchLoadoutUseCase {

    private final MatchStore matchStore;
    private final MemeCatalogPort memes;
    private final MatchViewAssembler views;

    @PreAuthorize("@gameAuthz.isPlayer(#roomId)")
    @Transactional
    public MatchLoadoutResponseDTO run(HotHatUser user, String roomId, SetMatchLoadoutRequestDTO request) {
        List<String> loadout = LoadoutRules.normalize(request.memeIds());
        if (loadout.size() != LoadoutRules.SIZE) {
            // Повтор внутри обоймы схлопывается нормализацией, и пяти слотов
            // не набирается: клиенту это надо назвать отдельным кодом.
            throw ApiException.of("MEME_DUPLICATE", 409);
        }
        // Одно чтение на всю обойму, а не пять подряд: пять вопросов «есть
        // такой мем?» на один запрос — тот самый веер одиночных чтений,
        // которого область партии избегает везде.
        if (!memes.existing(loadout).containsAll(loadout)) {
            throw ApiException.of("MEME_NOT_LOADED", 404);
        }
        MatchSession session = matchStore.open(roomId);
        if (session.state().getPhase() != MatchPhase.SETUP && session.state().getPhase() != MatchPhase.FINISHED) {
            // Обойма целиком меняется только между партиями: в идущей партии
            // очередь мемов уже раздана, и подменить её всю значило бы выдать
            // себе новые заряды. Для партии есть замена по одному слоту.
            throw ApiException.of("LOADOUT_LOCKED", 409);
        }
        MatchPlayer player = session.player(user.uid());
        player.setLoadout(loadout);
        // Боезапас и очередь выставляются стартовыми: партия ещё не идёт.
        LoadoutRules.arm(player);
        session.commit();
        return new MatchLoadoutResponseDTO(views.arsenal(player));
    }
}
