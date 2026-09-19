package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Рейтинги игрока по режимам.
 *
 * <p>Режимов ровно два, и они известны на этапе сборки, поэтому здесь два
 * поля, а не карта «режим → строка»: карту клиенту пришлось бы разбирать
 * по строковому ключу, а спецификации — описывать как «объект чего угодно».
 */
@Schema(description = "Рейтинги игрока в текущем сезоне")
public record PlayerRankingsView(

        @Schema(description = "Классический режим; null — в этом сезоне не играл", nullable = true)
        PlayerRankingView classic,

        @Schema(description = "Режим диверсий; null — в этом сезоне не играл", nullable = true)
        PlayerRankingView sabotage) {
}
