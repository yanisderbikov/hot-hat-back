package ru.hothat.game.store;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.game.port.RankedWordPort;
import ru.hothat.model.user.AppUser;
import ru.hothat.repository.GetterUser;
import ru.hothat.repository.SaverUser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Переходник к генератору рейтинговых слов и к истории выдачи.
 *
 * <p>История живёт в карточке игрока — чужой области. Партия её не правит
 * сама: просит запомнить выданное и не знает, где это лежит.
 */
@Component
@RequiredArgsConstructor
public class RankedWordAdapter implements RankedWordPort {

    /** Сколько последних слов помним на игрока: дальше повтор уже не заметен. */
    private static final int HISTORY_LIMIT = 520;

    private final RankedWordGenerator generator;
    private final GetterUser getterUser;
    private final SaverUser saverUser;

    @Override
    public List<String> generate(int count, List<String> playerUids, String language) {
        Map<String, AppUser> profiles = profiles(playerUids);
        List<List<String>> histories = new ArrayList<>();
        for (String uid : playerUids) {
            AppUser profile = profiles.get(uid);
            // Пустой список и на пропавшем профиле, и на пустой истории: в
            // jsonb-колонке может лежать null у строк, созданных до неё.
            histories.add(profile == null || profile.getRankedWordHistory() == null
                    ? List.of() : profile.getRankedWordHistory());
        }
        return generator.generate(count, histories, language);
    }

    @Override
    public void remember(List<String> playerUids, List<String> words, String language) {
        List<String> issued = words.stream().map(word -> generator.normalize(word, language)).toList();
        Map<String, AppUser> profiles = profiles(playerUids);
        for (String uid : playerUids) {
            // Строки может не быть вовсе. До переезда её заводил побочным
            // действием чек обоймы мемов; обойма ушла в область диверсий, и
            // теперь оболочку заводит тот, кто в неё пишет, — то есть здесь.
            AppUser profile = profiles.getOrDefault(uid, AppUser.builder().uid(uid).build());
            List<String> merged = new ArrayList<>();
            for (String word : profile.getRankedWordHistory() == null
                    ? List.<String>of() : profile.getRankedWordHistory()) {
                String key = generator.normalize(word, language);
                if (!key.isEmpty()) {
                    merged.add(key);
                }
            }
            merged.addAll(issued);
            profile.setRankedWordHistory(tail(merged));
            profile.setRankedWordHistoryUpdatedAt(Instant.now());
            saverUser.save(profile);
        }
    }

    /**
     * Карточки всего состава одним запросом.
     *
     * <p>Читать их по одной в цикле по составу нельзя: рейтинговая партия
     * спрашивает историю у каждого из восьмерых и на каждом ходе, и веер
     * одиночных чтений — то, чего область партии избегает везде (ср.
     * {@code MatchSession.players()}).
     */
    private Map<String, AppUser> profiles(List<String> playerUids) {
        Map<String, AppUser> byUid = new LinkedHashMap<>();
        for (AppUser profile : getterUser.getByUids(playerUids)) {
            byUid.put(profile.getUid(), profile);
        }
        return byUid;
    }

    /** Последние {@link #HISTORY_LIMIT} различных ключей, порядок сохранён. */
    private static List<String> tail(List<String> keys) {
        List<String> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = keys.size() - 1; i >= 0 && unique.size() < HISTORY_LIMIT; i--) {
            String key = keys.get(i);
            if (!key.isEmpty() && seen.add(key)) {
                unique.add(key);
            }
        }
        Collections.reverse(unique);
        return unique;
    }
}
