package ru.hothat.rating.api.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * У какого сезона спрашивают чемпиона.
 *
 * <p>Предела здесь нет: чемпион у сезона один, страницы взяться неоткуда.
 * Причины строковых типов — те же, что у {@link TeamRankingQueryDTO}.
 */
@Schema(description = "Параметры выбора сезона")
public record SeasonChampionQueryDTO(

        @Parameter(description = "Сезон года; по умолчанию текущий, он считается по UTC",
                example = "autumn")
        @Pattern(regexp = "^(winter|spring|summer|autumn)$", message = "Такого сезона нет.")
        String season,

        @Parameter(description = "Год сезона; по умолчанию текущий", example = "2026")
        @Min(value = 2026, message = "Рейтинги начались в 2026 году.")
        @Max(value = 2100, message = "Такого сезона ещё нет.")
        Integer year,

        @Parameter(description = "Режим партии; по умолчанию sabotage", example = "sabotage")
        @Pattern(regexp = "^(classic|sabotage)$", message = "Такого режима нет.")
        String mode,

        @Parameter(description = "Языковой дивизион; по умолчанию ru", example = "ru")
        @Pattern(regexp = "^(ru|en|de|es|fr|it|zh|ja|kk)$", message = "Такого дивизиона нет.")
        String division) {
}
