package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ник, закреплённый за игроком.
 *
 * <p>Возвращаем сохранённое значение, а не эхо запроса: сервер обрезает
 * пробелы и может отказать конфликтом, поэтому клиенту нужен именно тот ник,
 * который теперь лежит в базе.
 */
@Schema(description = "Занятый ник")
public record ClaimedNicknameResponseDTO(

        @Schema(description = "Ник, который теперь принадлежит игроку", example = "Vasya")
        String nickname) {
}
