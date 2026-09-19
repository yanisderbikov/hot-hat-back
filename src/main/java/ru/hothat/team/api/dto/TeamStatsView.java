package ru.hothat.team.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Показатели команды в обоих режимах.
 *
 * <p>Общая проекция: включается полем и в свою команду, и в чужой публичный
 * профиль.
 *
 * <p>Два именованных поля, а не список и не карта: режимов ровно два, они
 * закрыты перечислением {@link GameMode}, и оба всегда есть в ответе. Список
 * заставил бы клиент искать в нём нужный режим, а карта — верить, что ключ на
 * месте; сегодня он именно это и делает: {@code (r.stats||{}).classic||{}}.
 */
@Schema(description = "Показатели команды по режимам")
public record TeamStatsView(

        @Schema(description = "Классическая лига")
        TeamSeasonStatsView classic,

        @Schema(description = "Лига с диверсиями")
        TeamSeasonStatsView sabotage) {
}
