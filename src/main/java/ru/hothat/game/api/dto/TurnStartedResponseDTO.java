package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ход начат.
 *
 * <p>Слово тянет сервер: {@code drawRandomWord(bag)} в браузере означал, что
 * шляпу перемешивает тот, кто из неё тянет. Часы тоже серверные — вместе с
 * дедлайном приезжает и текущее серверное время, чтобы клиент сразу поправил
 * свои и не считал остаток по чужим часам.
 */
@Schema(description = "Начатый ход")
public record TurnStartedResponseDTO(

        @Schema(description = "Что произошло", example = "STARTED")
        TurnStartOutcome outcome,

        @Schema(description = "Ход; пусто, если партия закончилась", nullable = true)
        TurnView turn,

        @Schema(description = "Состояние партии после начала хода")
        MatchStateView match) {
}
