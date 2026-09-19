package ru.hothat.game.domain;

/** Итог закрытия хода — по кнопке «Завершить» или по серверным часам. */
public record TurnClosing(Outcome outcome, String turnId, long appealEndsAt, int preliminaryScore) {

    public enum Outcome {
        /** Ход закрыт, объявлено голосование. */
        CLOSED,
        /** Ход уже закрыт: повтор запроса при потере связи. */
        ALREADY_CLOSED,
        /** Время ещё не вышло: закрывать рано. */
        NOT_YET
    }
}
