package ru.hothat.testbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** Wire contract for both room status and historical paid Neuro jobs. */
@Schema(description = "Статус Нейроподмены: этапы GenAPI, доступность MP4 и сохранённые ID запросов")
public record NeuroJobResponseDTO(
        @Schema(description = "Постоянный ID задания Hot Hat") String jobId,
        @Schema(description = "Статус: queued, processing, interrupted, failed или ready") String status,
        @Schema(description = "Текущий этап генерации или восстановления") String stage,
        @Schema(description = "Доступен ли проверенный финальный MP4") boolean ready,
        @Schema(description = "Сохранённые ID оплаченных запросов GenAPI по имени модели")
        Map<String, String> providerRequests,
        @Schema(description = "Причина отказа или прерывания, если есть") String error,
        @Schema(description = "Исходная игровая комната, если это запись истории") String roomId,
        @Schema(description = "Время создания задания в ISO-8601 для истории") String createdAt,
        @Schema(description = "Сохранён ли итоговый ролик в S3") Boolean archived) {}
