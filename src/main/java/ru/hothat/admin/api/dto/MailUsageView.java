package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Расход почтовых отправлений. */
@Schema(description = "Расход почты")
public record MailUsageView(

        @Schema(description = "Ключ почтового сервиса задан: без него письма не уходят вовсе",
                example = "true", type = "boolean")
        boolean configured,

        @Schema(description = "Отправлено за сутки")
        UsageMetricView daily,

        @Schema(description = "Отправлено за месяц")
        UsageMetricView monthly) {
}
