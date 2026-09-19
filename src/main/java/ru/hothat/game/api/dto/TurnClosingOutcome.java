package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Чем кончилась попытка закрыть ход. */
@Schema(description = "Исход закрытия хода")
public enum TurnClosingOutcome {

    /** Ход закрыт, объявлено голосование. */
    CLOSED,
    /** Ход уже закрыт: повтор запроса при потере связи. */
    ALREADY_CLOSED,
    /** Время ещё не вышло: закрывать рано. */
    NOT_YET
}
