package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Расход раздачи статики. */
@Schema(description = "Расход раздачи статики")
public record HostingUsageView(

        @Schema(description = "Трафик за месяц")
        UsageMetricView transfer,

        @Schema(description = "Занятое место")
        UsageMetricView storage) {
}
