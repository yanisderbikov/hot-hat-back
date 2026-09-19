package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Подтверждение снимка расхода, снятого агентом мониторинга.
 *
 * <p>Заменяет половину {@code POST /api/monitor} — ту, что удостоверялась
 * секретом. Вторая половина того же адреса, для админа, живёт в консоли и
 * отдаёт снимок целиком: у неё другой читатель и другой экран. Агенту снимок
 * не нужен, он его не рисует; ему нужно знать, что снято и не пора ли будить
 * дежурного.
 */
@Schema(description = "Итог снимка расхода ресурсов")
public record MachineUsageSnapshotResponseDTO(

        @Schema(description = "Снят ли снимок")
        UsageSnapshotState state,

        @Schema(description = "Когда снимок снят, ISO-8601; null, если снимок пропущен",
                example = "2026-09-06T05:00:03Z", nullable = true)
        String collectedAt,

        @Schema(description = "Сколько писем о превышении порога отправлено этим снимком",
                example = "0", type = "integer")
        int thresholdAlertCount,

        @Schema(description = "Отправлен ли этим снимком суточный отчёт на почту",
                example = "false", type = "boolean")
        boolean dailyReportSent) {
}
