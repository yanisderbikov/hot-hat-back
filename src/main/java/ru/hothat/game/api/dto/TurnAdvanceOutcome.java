package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Чем кончилась передача очереди. */
@Schema(description = "Исход передачи очереди")
public enum TurnAdvanceOutcome {

    /** Очередь передана следующей команде. */
    ADVANCED,
    /** Шляпа пуста: партия закончена. */
    MATCH_FINISHED,
    /** Очередь уже передана: повтор запроса. */
    ALREADY_ADVANCED
}
