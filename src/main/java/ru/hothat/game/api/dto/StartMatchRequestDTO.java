package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Начать партию.
 *
 * <p>Слов в запросе нет и быть не может: шляпу собирает сервер из сданных
 * пачек, и приносить её с собой значило бы позволить хозяину комнаты заменить
 * чужие слова своими.
 */
@Schema(description = "Запрос на старт партии")
public record StartMatchRequestDTO(

        @Schema(description = "Первая партия комнаты или переигровка после итогов", example = "FIRST_GAME")
        @NotNull(message = "Нужно сказать, начинается партия или переигрывается.")
        MatchStartIntent intent) {
}
