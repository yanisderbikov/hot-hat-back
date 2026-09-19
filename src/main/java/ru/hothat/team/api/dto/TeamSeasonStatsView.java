package ru.hothat.team.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Показатели команды в одном режиме за текущий сезон.
 *
 * <p>Общая проекция: включается полем и в свою команду, и в чужой публичный
 * профиль. Раньше это были две разные карты с пересекающимся набором ключей,
 * собранные в двух методах одного класса.
 *
 * <p>Рейтинг живёт у самой команды и переходит из сезона в сезон, остальные
 * четыре числа — итог текущего сезона. Если команда в этом сезоне ещё не
 * играла, строки таблицы нет вовсе, и тогда здесь нули, а не null: «не играли»
 * и «сыграно ноль» для экрана — одно и то же, а два способа сказать это
 * заставляли клиент проверять наличие ключа.
 */
@Schema(description = "Показатели команды в одном режиме за сезон")
public record TeamSeasonStatsView(

        @Schema(description = "Режим, к которому относятся числа", example = "sabotage")
        GameMode mode,

        @Schema(description = "Рейтинг команды в этом режиме; стартовое значение — 1000",
                example = "1180", type = "integer")
        int rating,

        @Schema(description = "Очки за текущий сезон", example = "1240", type = "integer")
        int points,

        @Schema(description = "Сыграно партий за сезон", example = "38", type = "integer")
        int games,

        @Schema(description = "Побед за сезон", example = "21", type = "integer")
        int wins,

        @Schema(description = "Сколько раз за сезон команде засчитали техническое поражение за обрыв связи",
                example = "1", type = "integer")
        int technicalForfeits) {
}
