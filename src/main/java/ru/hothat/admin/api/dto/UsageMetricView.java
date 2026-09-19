package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Одна строка расхода: сколько занято из сколького.
 *
 * <p>Общая проекция снимка (§10.1): включается полем во все его разделы —
 * базу, раздачу статики, почту и VPS. Форма описана один раз, различаются
 * обёртки.
 *
 * <p>{@code used} и {@code limit} независимо необязательны, и это не небрежность:
 * у размера базы нет лимита (квоты кончились вместе с Firestore), а у
 * недоступной метрики нет значения, но лимит известен. Признак
 * {@code available} — это ровно {@code used != null}, он остаётся ради клиента,
 * который ветвится по нему сегодня.
 */
@Schema(description = "Метрика расхода ресурса")
public record UsageMetricView(

        @Schema(description = "Подпись метрики в админке", example = "PostgreSQL · размер базы")
        String label,

        @Schema(description = "Сколько израсходовано; null — источник значения не дал",
                example = "1073741824", type = "integer", nullable = true)
        Long used,

        @Schema(description = "Предел; null — предела нет или он не задан", example = "10737418240",
                type = "integer", nullable = true)
        Long limit,

        @Schema(description = "Сколько осталось; null — нет значения или нет предела",
                example = "9663676416", type = "integer", nullable = true)
        Long remaining,

        @Schema(description = "Доля израсходованного, проценты; null — нет значения или нет предела",
                example = "10.0", type = "number", nullable = true)
        Double percent,

        @Schema(description = "За какой отрезок посчитано")
        MetricPeriod period,

        @Schema(description = "Единица измерения; bytes — байты, остальное — штуки в подписи",
                example = "bytes")
        String unit,

        @Schema(description = "Насколько можно верить значению")
        MetricAccuracy accuracy,

        @Schema(description = "Значение есть. То же, что used != null: клиент ветвится по этому полю",
                example = "true", type = "boolean")
        boolean available,

        @Schema(description = "Почему метрика такая, какая есть; null — пояснять нечего",
                example = "Данные переехали в PostgreSQL: пооперационных квот больше нет.",
                nullable = true)
        String note,

        @Schema(description = "Откуда взято значение; null — источник не назван",
                example = "pg_database_size", nullable = true)
        String source) {
}
