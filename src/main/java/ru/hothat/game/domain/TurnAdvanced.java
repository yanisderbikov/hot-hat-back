package ru.hothat.game.domain;

/** Итог передачи очереди следующей команде. */
public record TurnAdvanced(Outcome outcome, MatchPhase phase, String currentTeamId, int wordsLeft) {

    public enum Outcome {
        /** Очередь передана: следующая команда получила карточку хода. */
        ADVANCED,
        /** Шляпа пуста: партия закончена. */
        MATCH_FINISHED,
        /** Очередь уже передана: повтор запроса. */
        ALREADY_ADVANCED
    }
}
