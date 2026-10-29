package ru.hothat.testbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** Owner-only read of previously submitted GenAPI request IDs. */
@Schema(description = "Бесплатная диагностика оплаченных этапов Нейроподмены через GenAPI GET")
public record NeuroProviderStatusResponseDTO(
        @Schema(description = "Постоянный ID задания Hot Hat") String jobId,
        @Schema(description = "Текущее состояние задания") String status,
        @Schema(description = "Отчёты по сохранённым ID GenAPI, без новых платных запросов")
        Map<String, ProviderStage> providerRequests,
        @Schema(description = "Пояснение, если ни одного ID GenAPI не было сохранено") String note) {

    @Schema(description = "Статус одного ранее отправленного задания провайдера")
    public record ProviderStage(
            @Schema(description = "ID запроса GenAPI") String requestId,
            @Schema(description = "Состояние обработки у провайдера") String status,
            @Schema(description = "Процент выполнения запроса") int progress,
            @Schema(description = "Цена провайдера, если он её сообщил") Double cost,
            @Schema(description = "Описание ошибки провайдера, если есть") String error) {}
}
