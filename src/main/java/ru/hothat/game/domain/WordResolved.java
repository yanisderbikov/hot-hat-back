package ru.hothat.game.domain;

/**
 * Итог разбора слова: угадали или пропустили.
 *
 * <p>{@code nextWord} видит только объясняющий, поэтому дальше по слоям едет
 * отдельным полем и вырезается для всех остальных.
 */
public record WordResolved(Outcome outcome,
                           String word,
                           String nextWord,
                           int turnScore,
                           int wordsLeft,
                           int teamScoreDelta,
                           boolean turnClosed,
                           long appealEndsAt) {

    public enum Outcome {
        /** Слово засчитано команде. */
        COUNTED,
        /** Слово возвращено в шляпу. */
        SKIPPED,
        /** Это слово уже разобрано этим же запросом: повтор ничего не изменил. */
        REPEATED,
        /** Ход истёк раньше нажатия: слово не засчитано, ход закрыт. */
        TURN_EXPIRED
    }
}
