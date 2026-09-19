package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Сколько игроков сейчас в портале. */
@Schema(description = "Сводка присутствия")
public record PresenceSummaryResponseDTO(

        /**
         * Не меньше единицы: спрашивающий уже онлайн, а показать ему «онлайн: 0»
         * означало бы сказать, что его самого здесь нет.
         */
        @Schema(description = "Сколько игроков отмечались за последние две минуты", example = "17", type = "integer", minimum = "1")
        long online,

        @Schema(description = "Время сервера, миллисекунды эпохи", example = "1788600000000", type = "integer")
        long serverNowMs) {
}
