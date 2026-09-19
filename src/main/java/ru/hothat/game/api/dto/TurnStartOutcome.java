package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Чем кончилась попытка начать ход. */
@Schema(description = "Исход начала хода")
public enum TurnStartOutcome {

    /** Ход начат, часы пошли. */
    STARTED,
    /** Ход уже идёт: повтор запроса при потере связи ничего не изменил. */
    ALREADY_RUNNING,
    /** Шляпа пуста: партия закончена. */
    MATCH_FINISHED
}
