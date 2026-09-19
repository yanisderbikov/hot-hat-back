package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Итог съёмки.
 *
 * <p>Присылает тот, кого снимали: плёнка пишется в его браузере, и знать, вышел
 * ли кадр, может только он. Неудачная съёмка стирается сразу — держать её
 * значило бы занимать слот заказчика впустую.
 */
@Schema(description = "Итог съёмки клипа")
public record ReportClipResultRequestDTO(

        @Schema(description = "Клип получился и годится к применению", example = "true")
        @NotNull(message = "Нужно сказать, удалась ли съёмка.")
        Boolean ready) {
}
