package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Метрики машины, на которой работает бекенд.
 *
 * <p>Собственный адрес вместо ветки внутри снимка мониторинга: снимок пишется
 * четыре раза в сутки и хранится, а это — состояние железа сию секунду, за
 * которым администратор жмёт «обновить». Читать хранимую историю ради текущей
 * загрузки процессора незачем.
 *
 * <p>Одна форма ответа на оба исхода (§10.1): на не-Linux {@code supported}
 * равен false, причина лежит в {@code unavailableReason}, а все измерения —
 * {@code null}. Прежний движок в этом случае возвращал объект другой формы, с
 * ключом {@code error} вместо всех метрик.
 */
@Schema(description = "Метрики хоста бекенда")
public record HostMetricsResponseDTO(

        @Schema(description = "Метрики доступны. На не-Linux их нет: считать нечего, /proc отсутствует",
                example = "true", type = "boolean")
        boolean supported,

        @Schema(description = "Почему метрик нет; null — они есть",
                example = "Метрики хоста доступны только на Linux (/proc)", nullable = true)
        String unavailableReason,

        @Schema(description = "Когда сняли показания", example = "1788600000000", type = "integer")
        long collectedAtMs,

        @Schema(description = "Корневой раздел диска; null — метрик нет", nullable = true)
        ByteUsageView disk,

        @Schema(description = "Оперативная память; null — метрик нет", nullable = true)
        ByteUsageView memory,

        @Schema(description = "Сколько байт машина приняла с момента старта ОС; null — метрик нет",
                example = "482196312064", type = "integer", nullable = true)
        Long networkRxBytes,

        @Schema(description = "Сколько байт машина отправила с момента старта ОС; null — метрик нет",
                example = "1902196312064", type = "integer", nullable = true)
        Long networkTxBytes,

        @Schema(description = "Сколько занимают локальные мем-видео; ноль — их на машине не осталось",
                example = "0", type = "integer", nullable = true)
        Long mediaBytes,

        @Schema(description = "Средняя загрузка за минуту; null — метрик нет", example = "0.42",
                type = "number", nullable = true)
        Double loadAverage,

        @Schema(description = "Сколько ядер у машины; null — метрик нет", example = "4",
                type = "integer", nullable = true)
        Integer cpuCores,

        @Schema(description = "Сколько сокетов открыто к LiveKit; null — посчитать не удалось",
                example = "18", type = "integer", nullable = true)
        Long livekitSockets,

        @Schema(description = "Сколько сокетов открыто к TURN; null — посчитать не удалось",
                example = "6", type = "integer", nullable = true)
        Long turnSockets,

        @Schema(description = "Службы машины: юнит и его порт вместе. Пустой список — метрик нет")
        List<HostServiceStateView> services) {
}
