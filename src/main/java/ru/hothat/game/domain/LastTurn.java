package ru.hothat.game.domain;

import java.util.List;
import java.util.Map;

/**
 * Закрытый ход: то, по чему голосует апелляция и что показывает экран итогов.
 *
 * <p>Хранится отдельно от текущего хода, потому что переживает его: пока идёт
 * голосование, поля {@code turnId}, {@code explainerUid} и счёт уже очищены,
 * а решать надо именно по ним.
 */
public record LastTurn(String turnId,
                       String teamId,
                       int score,
                       List<GuessedWord> guessedWords,
                       String explainerUid,
                       String guesserUid,
                       Integer finalScore,
                       List<String> invalidWordIds,
                       Map<String, Integer> rewards,
                       Map<String, Map<String, Integer>> specialRewardsByUid,
                       Long finalizedAtMs) {

    /** Ход только что закрыт: итоги ещё не подведены. */
    public static LastTurn opened(String turnId, String teamId, int score, List<GuessedWord> guessedWords,
                                  String explainerUid, String guesserUid) {
        return new LastTurn(turnId, teamId, score, List.copyOf(guessedWords), explainerUid, guesserUid,
                null, null, null, null, null);
    }

    /** Итоги подведены: апелляция закрыта, награды посчитаны. */
    public LastTurn finalized(int finalScore, List<String> invalidWordIds, Map<String, Integer> rewards,
                              Map<String, Map<String, Integer>> specialRewardsByUid, long finalizedAtMs) {
        return new LastTurn(turnId, teamId, score, guessedWords, explainerUid, guesserUid,
                finalScore, List.copyOf(invalidWordIds), Map.copyOf(rewards),
                Map.copyOf(specialRewardsByUid), finalizedAtMs);
    }

    /** Итоги уже подведены: закрывать апелляцию второй раз нечего. */
    public boolean settled() {
        return finalizedAtMs != null;
    }
}
