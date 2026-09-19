package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Чем кончилось подведение итогов. */
@Schema(description = "Исход подведения итогов")
public enum AppealClosingOutcome {

    /** Итоги подведены: слова отменены, награды начислены, очередь передана. */
    SETTLED,
    /** Итоги уже подведены раньше: повтор ничего не начислил. */
    ALREADY_SETTLED,
    /** Голосование ещё идёт. */
    STILL_OPEN
}
