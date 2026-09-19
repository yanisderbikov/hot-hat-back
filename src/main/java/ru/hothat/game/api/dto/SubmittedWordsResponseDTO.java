package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Сохранённые слова.
 *
 * <p>Возвращается своя пачка целиком — ровно в том виде, как её сохранил
 * сервер после отбрасывания повторов. Клиент подставляет её в поле ввода, и
 * расхождение между показанным и сохранённым исчезает.
 *
 * <p>Чужие слова не отдаются никому: виден только общий счётчик шляпы.
 */
@Schema(description = "Итог сдачи слов")
public record SubmittedWordsResponseDTO(

        @Schema(description = "Все свои слова после сохранения",
                example = "[\"Абажур\",\"Водопад\",\"Кофемолка\"]")
        List<String> words,

        @Schema(description = "Сколько слов сдал этот игрок", example = "3")
        int mine,

        @Schema(description = "Сколько слов в шляпе всего", example = "47")
        int total) {
}
