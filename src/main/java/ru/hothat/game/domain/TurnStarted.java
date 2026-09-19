package ru.hothat.game.domain;

/**
 * Итог попытки начать ход.
 *
 * <p>Одна форма ответа на три исхода: ход начат, ход уже идёт (повтор запроса
 * при потере связи), шляпа опустела и партия закончена. Клиенту достаточно
 * посмотреть на {@code outcome}, а не гадать по набору пришедших полей.
 */
public record TurnStarted(Outcome outcome, String turnId, String word, long deadlineMs, double durationSeconds) {

    public enum Outcome {
        /** Ход начат: слово вытянуто, часы пошли. */
        STARTED,
        /** Ход этого игрока уже идёт: повтор ничего не меняет. */
        ALREADY_RUNNING,
        /** Шляпа пуста: начинать нечего, партия закончена. */
        MATCH_FINISHED
    }
}
