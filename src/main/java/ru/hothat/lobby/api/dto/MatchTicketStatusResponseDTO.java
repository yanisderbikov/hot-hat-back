package ru.hothat.lobby.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Что стало с заявкой.
 *
 * <p>Заменяет повторный вызов {@code portal matchmake} ради опроса раз в
 * три-пять секунд. Это чистое чтение: оно ничего не пересобирает и не может
 * переселить игрока в другую комнату между двумя опросами.
 */
@Schema(description = "Состояние заявки на подбор")
public record MatchTicketStatusResponseDTO(

        @Schema(description = "Прочитанная заявка")
        MatchTicketView ticket) {
}
