package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Голос за отмену слова.
 *
 * <p>Прислать надо желаемое значение, а не «переключить». Раньше клиент
 * вычислял новое значение сам от своего снимка, и на разъехавшемся снимке
 * второе нажатие отправляло то, что уже стоит: человек видел снятый голос, а
 * на сервере он оставался.
 */
@Schema(description = "Запрос на подачу голоса")
public record CastAppealVoteRequestDTO(

        @Schema(description = "Отменить это слово (true) или снять свой голос (false)", example = "true")
        @NotNull(message = "Нужно сказать, отменяете ли вы слово.")
        Boolean cancelWord) {
}
