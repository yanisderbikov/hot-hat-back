package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Сдать слова в шляпу.
 *
 * <p>Слова добавляются к уже сданным, а не заменяют их: экран набора шлёт то,
 * что человек напечатал сейчас, и заменять всю пачку значило бы терять
 * сохранённое в прошлый заход. Повторы и пустые строки сервер выбрасывает сам.
 */
@Schema(description = "Слова в шляпу")
public record SubmitWordsRequestDTO(

        @Schema(description = "Слова и короткие фразы, до 80 знаков каждое",
                example = "[\"Абажур\",\"Водопад\",\"Кофемолка\"]")
        @NotNull(message = "Нужно прислать список слов.")
        @NotEmpty(message = "Список слов пуст.")
        @Size(max = 100, message = "За один раз принимается не больше 100 слов.")
        List<@NotNull @Size(min = 1, max = 80, message = "Слово длиннее 80 знаков.") String> words) {
}
