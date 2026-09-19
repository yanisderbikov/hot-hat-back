package ru.hothat.game.domain;

import java.util.List;
import java.util.Map;

/**
 * Итог голосования: сколько слов отменили и что за это причитается.
 *
 * <p>Награды здесь только посчитаны. Начислить их движок не может: боезапас
 * лежит у игроков, а это уже запись в базу.
 *
 * @param teamScoreDelta поправка к счёту команды — отрицательная либо ноль:
 *                       очко за слово начисляется сразу, апелляция только вычитает
 */
public record AppealClosed(Outcome outcome,
                           String turnId,
                           String teamId,
                           int finalScore,
                           List<String> invalidWordIds,
                           List<String> returnedWords,
                           Map<String, Integer> rewards,
                           Map<String, Map<String, Integer>> specialRewardsByUid,
                           List<String> rewardedUids,
                           int teamScoreDelta,
                           boolean matchFinished) {

    public enum Outcome {
        /** Итоги подведены: награды начислены, очередь передана. */
        SETTLED,
        /** Итоги уже подведены раньше: повтор запроса ничего не начисляет. */
        ALREADY_SETTLED,
        /** Голосование ещё идёт. */
        STILL_OPEN
    }
}
