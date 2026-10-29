package ru.hothat.codeteam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Превью бесплатно; live — одна оплачиваемая генерация GenAPI. */
@Schema(description = "Результат бесплатного предварительного просмотра или оплаченного анализа команды")
public record CodeTeamPilotResponseDTO(
        @Schema(description = "Режим выполнения: проверка или реальный вызов") String mode,
        @Schema(description = "Модель GenAPI, выполнившая анализ") String model,
        @Schema(description = "Количество подтверждённых событий, использованных моделью") int evidenceCount,
        @Schema(description = "Подготовленный сервером промпт для анализа") String prompt,
        @Schema(description = "Ответ модели либо диагностический результат") String analysis) {
}
