package ru.hothat.team.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Нажать «готов» или снять готовность.
 *
 * <p>Один адрес на оба исхода, а не два: это не два разных события, а одно
 * значение переключателя, и клиент рисует его именно переключателем. Раньше
 * он вычислял новое значение сам — {@code ready: !preflight?.ready?.[uid]} —
 * и при разъехавшемся снимке отправлял то, что уже стоит.
 */
@Schema(description = "Запрос на смену готовности")
public record SetPreflightReadinessRequestDTO(

        /**
         * Обёрточный тип с {@code @NotNull}, а не примитив: пропущенное поле
         * иначе означало бы «снять готовность», и пустое тело тихо
         * разворачивало бы уже готового участника.
         */
        @Schema(description = "Готов ли участник начинать. Поставить true можно только при "
                + "подтверждённой связи", example = "true")
        @NotNull(message = "Нужно сказать, готовы ли вы.")
        Boolean ready) {
}
