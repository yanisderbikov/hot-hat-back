package ru.hothat.team.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Отчёт о своей связи: камера и микрофон отвечают или нет.
 *
 * <p>Идентификатора команды в теле нет, хотя старое действие его принимало:
 * команду сервер знает по токену, а доверять клиенту в вопросе «за какую
 * команду я отчитываюсь» незачем.
 */
@Schema(description = "Отчёт о состоянии камеры и микрофона")
public record ReportPreflightMediaRequestDTO(

        /**
         * Обёрточный тип с {@code @NotNull}, а не примитив: пропущенное поле
         * иначе стало бы {@code false}, и участник, у которого всё работает,
         * молча сбрасывал бы свою готовность из-за опечатки в теле запроса.
         */
        @Schema(description = "Камера и микрофон отвечают", example = "true")
        @NotNull(message = "Нужно сказать, работает ли связь.")
        Boolean mediaOk) {
}
