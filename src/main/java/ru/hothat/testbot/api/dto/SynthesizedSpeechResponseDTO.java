package ru.hothat.testbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Озвученная реплика бота.
 *
 * <p>Звук едет строкой base64 внутри JSON, а не отдельным файлом. Так уже
 * устроен старый ответ, и менять это здесь не время: клиент скармливает
 * строку прямо в {@code new Audio("data:…;base64,…")}
 * ({@code app-core.js:2126}), а реплики короткие — до восьмидесяти знаков,
 * то есть несколько десятков килобайт. Отдельный адрес за файлом стоил бы
 * второго обращения на каждое «облако мыслей».
 *
 * <p>Голос возвращается трижды и по-разному не случайно: {@code voiceId} —
 * то, что клиент попросил (или получил взамен неизвестного), {@code voiceLabel} —
 * как назвать голос человеку, {@code voiceName} — что именно синтезировало
 * фразу. Последнее нужно техлогу игры: он записывает голос в событие
 * {@code sabotage.thought_voice}, и без имени модели по логу нельзя понять,
 * почему бот вдруг зазвучал иначе.
 */
@Schema(description = "Озвученная реплика бота")
public record SynthesizedSpeechResponseDTO(

        @Schema(description = "Тип содержимого звука", example = "audio/mpeg")
        String mime,

        @Schema(description = "Сам звук в base64: клиент проигрывает его как data-URL",
                example = "SUQzBAAAAAAAI1RTU0UAAAAPAAADTGF2ZjU4Ljc2LjEwMAAAAAAAAAAAAAAA")
        String audioBase64,

        @Schema(description = "Каким голосом озвучено: 1 — мужской, 2 — женский, 3 — смешной",
                example = "1", allowableValues = {"1", "2", "3"})
        String voiceId,

        @Schema(description = "Название голоса для человека", example = "Мужской")
        String voiceLabel,

        @Schema(description = "Модель синтеза, которой сделан звук", example = "ru-RU-DmitryNeural")
        String voiceName) {
}
