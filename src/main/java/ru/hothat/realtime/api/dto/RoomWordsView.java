package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Свои слова в шляпе — та же форма, что у ответа
 * {@code PUT /api/v2/game/{roomId}/word-submissions/me}.
 *
 * <p>Заменяет подписку на собственный документ {@code rooms/{id}/wordSubmissions/{uid}}
 * ({@code app-core.js:8876}), которой поле ввода восстанавливалось после
 * перезагрузки вкладки.
 *
 * <p>Чужие слова не отдаются никому: виден только общий счётчик шляпы. У
 * зрителя список пуст — сдавать слова ему нечего.
 */
@Schema(description = "Свои слова в шляпе")
public record RoomWordsView(

        @Schema(description = "Все свои слова, как их сохранил сервер",
                example = "[\"Абажур\",\"Водопад\",\"Кофемолка\"]")
        List<String> words,

        @Schema(description = "Сколько слов сдал слушатель", example = "3", type = "integer")
        int mine,

        @Schema(description = "Сколько слов в шляпе всего", example = "47", type = "integer")
        int total) {
}
