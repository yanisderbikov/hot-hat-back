package ru.hothat.game.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Награды за ход: обычные — за счёт, редкие — за номер угаданного слова.
 *
 * <p>Помидоры и мемы идут парами за каждые два и три слова, голосовой эффект
 * — вместе с мемом, крокодил — за каждые пять. Редкое оружие выдаётся не за
 * счёт хода, а по сквозному номеру слова команды за партию: четвёртое,
 * шестое, восьмое, девятое, одиннадцатое и тринадцатое.
 *
 * <p>Номера редких наград — простые числа и их близкие соседи не случайно:
 * совпадений между ними мало, поэтому два редких оружия за одно слово —
 * редкость, а не правило.
 */
public final class RewardRules {

    /** Номер слова, кратность которому даёт редкую награду, — по ключу оружия. */
    private static final Map<String, Integer> SPECIAL_EVERY = specialEvery();

    private RewardRules() {
    }

    private static Map<String, Integer> specialEvery() {
        Map<String, Integer> every = new LinkedHashMap<>();
        every.put("negative", 4);
        every.put("apozh", 6);
        every.put("replacement", 8);
        every.put("object", 9);
        every.put("poop", 11);
        every.put("megaText", 13);
        // Порядок объявления держит порядок выдачи: за одно слово может
        // прилететь два редких оружия сразу, и клиент показывает их подряд.
        return Collections.unmodifiableMap(every);
    }

    /** Помидоры, мемы, голоса и крокодилы за счёт хода. */
    public static Map<String, Integer> forScore(int score) {
        int guessed = Math.max(0, score);
        int memes = (guessed / 3) * 2;
        Map<String, Integer> reward = new LinkedHashMap<>();
        reward.put("tomato", (guessed / 2) * 2);
        reward.put("meme", memes);
        // Голосовой эффект идёт в комплекте с мемом: их тратят вместе.
        reward.put("voice", memes);
        reward.put("crocodile", guessed / 5);
        return reward;
    }

    /**
     * Редкие награды команде за слова с номера {@code previousScore + 1} по
     * {@code nextScore}. Счёт сквозной по партии, поэтому и хранится по команде.
     *
     * <p>Порядок ответа — по номеру слова, а внутри слова — по порядку
     * объявления оружия: за один ход команда может перешагнуть сразу
     * несколько номеров, и выдать их надо все.
     */
    public static List<String> specialsBetween(int previousScore, int nextScore) {
        int from = Math.max(0, previousScore);
        int to = Math.max(from, nextScore);
        List<String> earned = new ArrayList<>();
        for (int wordNumber = from + 1; wordNumber <= to; wordNumber++) {
            for (Map.Entry<String, Integer> rule : SPECIAL_EVERY.entrySet()) {
                if (wordNumber % rule.getValue() == 0) {
                    earned.add(rule.getKey());
                }
            }
        }
        return earned;
    }

    /**
     * Раздача редких наград по кругу внутри команды.
     *
     * <p>Курсор нужен затем, что иначе все редкие награды партии достаются
     * тому, кто в списке состава первый: раздача идёт по одному и тому же
     * списку каждый раз.
     *
     * @param roster живые игроки команды; у тест-ботов редкого оружия нет
     */
    public static SpecialGrant distribute(List<String> specials, List<String> roster, int cursor) {
        Map<String, Map<String, Integer>> byUid = new LinkedHashMap<>();
        int nextCursor = Math.max(0, cursor);
        if (roster.isEmpty()) {
            return new SpecialGrant(byUid, nextCursor);
        }
        for (String special : specials) {
            String recipient = roster.get(nextCursor % roster.size());
            nextCursor++;
            byUid.computeIfAbsent(recipient, uid -> emptySpecials()).merge(special, 1, Integer::sum);
        }
        return new SpecialGrant(byUid, nextCursor);
    }

    /**
     * Полный набор ключей с нулями, а не только выданное. Клиент рисует
     * прибавку по каждому ключу, и отсутствие ключа он показал бы пустотой
     * вместо нуля.
     */
    private static Map<String, Integer> emptySpecials() {
        Map<String, Integer> zeros = new LinkedHashMap<>();
        for (String key : WeaponRegistry.specialAmmoKeys()) {
            zeros.put(key, 0);
        }
        return zeros;
    }

    /** Кому что досталось и куда сместился курсор команды. */
    public record SpecialGrant(Map<String, Map<String, Integer>> byUid, int nextCursor) {
    }
}
