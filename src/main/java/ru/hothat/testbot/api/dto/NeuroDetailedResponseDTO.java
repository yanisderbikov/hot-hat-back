package ru.hothat.testbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Named, typed OpenAPI contracts for the owner's read-only Neuro diagnostics.
 * These record types describe the stable keys already returned by the service;
 * they do not change any runtime request, billable operation, or response body.
 */
public final class NeuroDetailedResponseDTO {
    private NeuroDetailedResponseDTO() {}

    @Schema(name = "NeuroUploadConfigResponse",
            description = "Бесплатная конфигурация одновидео Нейроподмены перед отдельным согласием на оплату")
    public record UploadConfig(
            @Schema(description = "Заранее заданная последовательность действий") String assignment,
            @Schema(description = "Точный текст русской речи") String spokenText,
            @Schema(description = "Длительность планируемого результата в секундах") int durationSeconds,
            @Schema(description = "Максимальный размер исходного видео в байтах") long maximumSourceBytes,
            @Schema(description = "Предварительная стоимость попытки в рублях без гарантии списания") int priceEstimateRoubles,
            @Schema(description = "Указывает, что цена является оценкой") boolean priceIsEstimate,
            @Schema(description = "Доступность PostgreSQL и архива S3") NeuroReadinessResponseDTO readiness,
            @Schema(description = "Включена ли оплачиваемая цепочка GenAPI") boolean providerReady) {}

    @Schema(name = "NeuroVideoCharacterCapabilityResponse",
            description = "Бесплатная проверка возможности видеоэлемента Kling O3 без отправки генерации")
    public record VideoCharacterCapability(
            @Schema(description = "Вариант интеграции видеоэлемента") String variant,
            @Schema(description = "Длительность подготовленного видеоэлемента в секундах") double referenceSeconds,
            @Schema(description = "Доступны ли исходные видео и голос") boolean sourceVideoAndVoice,
            @Schema(description = "Можно ли бесплатно подготовить MP4 для просмотра") boolean freeLocalVideoPreview,
            @Schema(description = "Режим элементов описан в документации GenAPI") boolean genApiO3AcceptsExistingElements,
            @Schema(description = "Подтверждена ли настоящая генерация видеоперсонажа") boolean genApiVideoCharacterCreationVerified,
            @Schema(description = "Сконфигурирован ли отдельный официальный Kling API") boolean officialKlingApiConfigured,
            @Schema(description = "Разрешены ли платные запросы интеграции") boolean paidGenerationEnabled,
            @Schema(description = "Список задействованных сервисов GenAPI") String provider,
            @Schema(description = "Режим генерации O3") String referenceMode,
            @Schema(description = "Создан ли постоянный видеоперсонаж у провайдера") boolean persistentKlingVideoElementCreated,
            @Schema(description = "Подтверждено ли принятие длительности провайдером") boolean providerDurationVerified,
            @Schema(description = "Предварительная цена Kling в рублях за секунду") int costEstimateRoublesPerSecond,
            @Schema(description = "Код готовности или причины блокировки") String reason,
            @Schema(description = "Нужная операция для полноценной подмены") String requiredProviderOperation,
            @Schema(description = "Ограничения точности и гарантий провайдера") String warning,
            @Schema(description = "Бесплатная проверка не создаёт платные POST") boolean noPaidPostsDuringPreflight) {}

    @Schema(name = "NeuroKlingReferencePreflightResponse",
            description = "Проверка сохранённого референса без оплачиваемых вызовов GenAPI")
    public record KlingReferencePreflight(
            @Schema(description = "ID существующего задания Нейроподмены") String jobId,
            @Schema(description = "Число оплачиваемых вызовов в этой проверке, всегда ноль") int paidCalls,
            @Schema(description = "Была ли запущена генерация, всегда false") boolean generationStarted,
            @Schema(description = "Разрешена ли повторная отправка после проверки") boolean safeToRetry,
            @Schema(description = "Результат проверки видео и публичной ссылки") String check,
            @Schema(description = "Содержит ли референс видеопоток") Boolean inputVideo,
            @Schema(description = "Реальная длительность проверенного видео") Double inputSeconds,
            @Schema(description = "Размер видеореференса в байтах") Long inputBytes,
            @Schema(description = "Скачивается ли публичный MP4 через HTTPS") Boolean signedUrlReachable,
            @Schema(description = "HTTP-статус публичного скачивания") Integer publicGetStatus,
            @Schema(description = "Распознана ли сигнатура MP4") Boolean mp4Signature,
            @Schema(description = "Режим Kling для нового видео") String model,
            @Schema(description = "Запланированная длительность итогового видео") Integer durationSeconds,
            @Schema(description = "Безопасное техническое описание неудачи") String technicalReason) {}

    @Schema(name = "NeuroKlingActingQualityResponse",
            description = "Бесплатная проверка качества уже оплаченного Kling без повторного вызова")
    public record KlingActingQuality(
            @Schema(description = "ID задания Нейроподмены") String jobId,
            @Schema(description = "Новые платные вызовы, всегда ноль") int paidCalls,
            @Schema(description = "Обнаружено ли копирование исходных движений") Boolean copyDetected,
            @Schema(description = "Соответствует ли результат длительности сценария") boolean durationValid,
            @Schema(description = "Измеренная длительность итогового видео") Double actualDurationSeconds,
            @Schema(description = "Ожидаемая длительность результата") Integer expectedDurationSeconds,
            @Schema(description = "Коэффициент сходства кадров SSIM") Double ssim,
            @Schema(description = "Код результата проверки актёрской сцены") String check) {}

    @Schema(name = "NeuroReferenceMediaPreflightResponse",
            description = "Бесплатная проверка открытости MP4 и первого JPEG для провайдера")
    public record ReferenceMediaPreflight(
            @Schema(description = "ID существующего задания") String jobId,
            @Schema(description = "Число оплачиваемых вызовов, всегда ноль") int paidCalls,
            @Schema(description = "HTTP-статус скачивания видеоэлемента") Integer videoPublicGet,
            @Schema(description = "HTTP-статус скачивания стартового изображения") Integer startImagePublicGet,
            @Schema(description = "Результат проверки обоих референсов") String check,
            @Schema(description = "Техническая причина недоступности референса") String technicalReason) {}
}
