package ru.hothat.rating.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Что сделал зачёт результата.
 *
 * <p>Одна форма на все исходы: ветвиться клиенту нужно по {@code outcome},
 * а не по наличию ключей. Поля, которых у исхода нет, приезжают пустыми —
 * см. описание каждого.
 */
@Schema(description = "Итог зачёта результата партии")
public record SubmittedMatchResultResponseDTO(

        @Schema(description = "Чем кончился зачёт")
        MatchResultOutcome outcome,

        @Schema(description = "Таблица сезона, в которую попал результат; null при повторном зачёте — "
                + "первый зачёт уже сказал, куда он лёг",
                example = "2026-autumn-sabotage-ru", nullable = true)
        String rankingId,

        @Schema(description = "Дивизион этой таблицы; null при повторном зачёте и при аннулировании",
                example = "ru", nullable = true,
                allowableValues = {"ru", "en", "de", "es", "fr", "it", "zh", "ja", "kk"})
        String divisionLanguage,

        @Schema(description = "Партия закончилась технически, из-за обрыва связи; null — исход "
                + "об этом не сообщает (повторный зачёт, аннулирование)",
                example = "false", type = "boolean", nullable = true)
        Boolean technical,

        @Schema(description = "Команды, которым засчитали техническое поражение: кто-то из состава "
                + "пропал из партии. Пустой список — виновных нет либо очки не считались",
                example = "[]")
        List<String> culpritTeamIds) {
}
