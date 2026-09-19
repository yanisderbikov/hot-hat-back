package ru.hothat.game.store;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import ru.hothat.util.Divisions;
import ru.hothat.util.Shuffle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Наборы слов для рейтинговой партии: девять словарей по дивизионам.
 *
 * <p>Генератор лежит в {@code store} области партии: словарь — такой же
 * внешний источник, как таблица, только лежит он в ресурсах сборки. Правило,
 * ради которого он существует, одно: игроку не должно достаться слово,
 * которое он уже объяснял, — поэтому на вход идут истории всего состава.
 *
 * <p>Форматы намеренно смешиваются: примерно половина одиночных слов, треть
 * словосочетаний и пятая часть фраз из трёх и более слов. Без этого партия
 * съезжает в один формат и становится однообразной.
 *
 * <p>Запреты снимаются постепенно: сначала не берём ничего из историй вовсе,
 * потом только недавнее, потом берём что есть. Иначе набор было бы не собрать
 * там, где состав уже видел половину словаря.
 */
@Slf4j
@Component
public class RankedWordGenerator {

    private final Map<String, List<String>> pools = new HashMap<>();

    @PostConstruct
    void load() {
        for (String code : Divisions.CODES) {
            pools.put(code, read("words/" + code + ".txt"));
        }
        log.info("Загружены словари: {}", pools.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue().size()).sorted().toList());
    }

    private List<String> read(String path) {
        List<String> words = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (!word.isEmpty()) {
                    words.add(word);
                }
            }
        } catch (IOException e) {
            log.error("Не удалось прочитать словарь {}", path, e);
        }
        return List.copyOf(new LinkedHashSet<>(words));
    }

    /** Ключ сравнения с историей: локаль дивизиона и ё→е для русского. */
    public String normalize(String value, String language) {
        String lang = Divisions.normalize(language == null ? "ru" : language);
        Locale locale = Locale.forLanguageTag(Divisions.meta(lang).locale());
        String word = (value == null ? "" : value).trim().toLowerCase(locale).replaceAll("\\s+", " ");
        if ("ru".equals(lang)) {
            word = word.replace('ё', 'е');
        }
        return word;
    }

    /**
     * Набор из {@code count} слов, которых нет в историях состава.
     *
     * @param playerHistories уже выданные каждому игроку слова
     * @param language        дивизион, из пула которого берутся слова
     */
    public List<String> generate(int count, List<List<String>> playerHistories, String language) {
        int need = Math.max(1, Math.min(100, count));
        String lang = Divisions.normalize(language == null ? "ru" : language);
        List<String> sourcePool = pools.getOrDefault(lang, pools.get("en"));

        List<List<String>> histories = new ArrayList<>();
        for (List<String> raw : playerHistories == null ? List.<List<String>>of() : playerHistories) {
            List<String> normalized = new ArrayList<>();
            for (String word : raw == null ? List.<String>of() : raw) {
                String key = normalize(word, lang);
                if (!key.isEmpty()) {
                    normalized.add(key);
                }
            }
            histories.add(normalized);
        }
        Set<String> strictBlocked = new HashSet<>();
        Set<String> recentBlocked = new HashSet<>();
        for (List<String> history : histories) {
            strictBlocked.addAll(history);
            recentBlocked.addAll(history.subList(Math.max(0, history.size() - 220), history.size()));
        }

        List<String> selected = new ArrayList<>();
        Set<String> selectedKeys = new HashSet<>();

        List<String> singles = sourcePool.stream().filter(w -> wordParts(w) == 1).toList();
        List<String> doubles = sourcePool.stream().filter(w -> wordParts(w) == 2).toList();
        List<String> triples = sourcePool.stream().filter(w -> wordParts(w) >= 3).toList();

        // Разнообразие форматов: ~50% одиночных слов, 30% словосочетаний, 20% фраз из 3+ слов.
        int tripleNeed = Math.max(1, Math.round(need * 0.20f));
        int doubleNeed = Math.max(1, Math.round(need * 0.30f));
        int singleNeed = Math.max(0, need - tripleNeed - doubleNeed);

        List<Object[]> targets = List.of(
                new Object[]{singles, singleNeed, 1},
                new Object[]{doubles, doubleNeed, 2},
                new Object[]{triples, tripleNeed, 3});

        for (Set<String> blocked : List.of(strictBlocked, recentBlocked, Set.<String>of())) {
            for (Object[] target : targets) {
                @SuppressWarnings("unchecked")
                List<String> pool = (List<String>) target[0];
                int amount = (int) target[1];
                int category = (int) target[2];
                long already = selected.stream().filter(w -> matchesCategory(w, category)).count();
                if (already < amount) {
                    addCategory(pool, amount - (int) already, blocked, selected, selectedKeys, need, lang);
                }
            }
            if (selected.size() >= need) {
                break;
            }
        }

        if (selected.size() < need) {
            for (String word : Shuffle.of(sourcePool)) {
                if (selected.size() >= need) {
                    break;
                }
                String key = normalize(word, lang);
                if (key.isEmpty() || selectedKeys.contains(key)) {
                    continue;
                }
                selected.add(word);
                selectedKeys.add(key);
            }
        }
        List<String> shuffled = Shuffle.of(selected);
        return new ArrayList<>(shuffled.subList(0, Math.min(need, shuffled.size())));
    }

    private void addCategory(List<String> pool, int amount, Set<String> blocked, List<String> selected,
                             Set<String> selectedKeys, int need, String lang) {
        int added = 0;
        for (String word : Shuffle.of(pool)) {
            if (added >= amount || selected.size() >= need) {
                break;
            }
            String key = normalize(word, lang);
            if (key.isEmpty() || selectedKeys.contains(key) || blocked.contains(key)) {
                continue;
            }
            selected.add(word);
            selectedKeys.add(key);
            added++;
        }
    }

    private static boolean matchesCategory(String word, int category) {
        int parts = wordParts(word);
        return category == 3 ? parts >= 3 : parts == category;
    }

    private static int wordParts(String word) {
        return word.trim().split("\\s+").length;
    }
}
