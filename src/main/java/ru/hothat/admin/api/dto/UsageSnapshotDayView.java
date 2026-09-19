package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Один день истории: дата и снимок, сохранённый за этот день последним. */
@Schema(description = "День истории расхода")
public record UsageSnapshotDayView(

        @Schema(description = "День, UTC", example = "2026-09-05", format = "date")
        String day,

        @Schema(description = "Последний снимок за этот день; null — день есть в истории, "
                + "но снимок в нём не сохранился", nullable = true)
        UsageSnapshotView snapshot) {
}
