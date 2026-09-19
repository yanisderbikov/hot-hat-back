package ru.hothat.rating.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Победитель завершённого сезона.
 *
 * <p>Хранится отдельным полем таблицы сезона и заполняется вручную при её
 * закрытии: ни один сценарий сервиса чемпиона не пишет. Поэтому гарантировано
 * здесь только имя — его показывает шапка страницы рейтингов; остальное может
 * отсутствовать у старых сезонов.
 */
@Schema(description = "Чемпион сезона")
public record SeasonChampionView(

        @Schema(description = "Идентификатор команды-чемпиона; null — в записи сезона его нет",
                example = "0f3a9c1d-7b2e-4a58-9c40-6d5e2b8a1f37", nullable = true)
        String teamId,

        @Schema(description = "Название команды-чемпиона", example = "Шляпные волки")
        String name,

        @Schema(description = "Логотип как data-URL; null — логотипа нет",
                example = "data:image/webp;base64,UklGRhIAAABXRUJQ", nullable = true)
        String logoDataUrl) {
}
