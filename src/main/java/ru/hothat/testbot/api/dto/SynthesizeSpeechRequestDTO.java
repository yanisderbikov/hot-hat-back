package ru.hothat.testbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Реплика «облака мыслей» бота, которую надо озвучить.
 *
 * <p>Заменяет тело {@code POST /api/tts} ({@code app-core.js:2106}).
 *
 * <p>Длина ограничена восемьюдесятью знаками — ровно столько берёт движок
 * ({@code EdgeTtsService.MAX_TEXT_CHARS}), остальное он молча отрезал. Молча
 * терять половину фразы хуже, чем отказать: здесь длинная реплика — ошибка
 * разбора с внятным текстом.
 *
 * <p>Поле {@code voiceId} названо по-новому, без старого {@code voice_id}:
 * область {@code /api/v2} везде говорит camelCase, и заводить синоним ради
 * адреса, которого ни один клиент ещё не зовёт, незачем.
 */
@Schema(description = "Реплика бота для озвучки")
public record SynthesizeSpeechRequestDTO(

        @Schema(description = "Что произнести. Одна короткая фраза «облака мыслей»",
                example = "Кажется, это что-то круглое", maxLength = 80)
        @NotBlank(message = "Нужен текст реплики.")
        @Size(max = 80, message = "Реплика длиннее 80 знаков не озвучивается.")
        String text,

        @Schema(description = "Голос: 1 — мужской, 2 — женский, 3 — смешной. "
                + "Пусто — мужской", example = "1",
                allowableValues = {"1", "2", "3"}, nullable = true)
        @Pattern(regexp = "^[123]$", message = "Неизвестный голос.")
        String voiceId) {
}
