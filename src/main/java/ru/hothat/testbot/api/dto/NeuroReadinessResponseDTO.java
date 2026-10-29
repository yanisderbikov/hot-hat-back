package ru.hothat.testbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Owner-visible no-charge readiness state. */
@Schema(description = "Бесплатная предпроверка устойчивого хранения Нейроподмен перед оплатой")
public record NeuroReadinessResponseDTO(
        @Schema(description = "S3-хранилище видео и голоса настроено") boolean archiveReady,
        @Schema(description = "Подключена база заданий PostgreSQL") boolean databaseReady) {}
