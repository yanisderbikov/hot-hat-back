package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Занято из общего — в байтах. */
@Schema(description = "Расход в байтах")
public record ByteUsageView(

        @Schema(description = "Занято", example = "37580963840", type = "integer")
        long usedBytes,

        @Schema(description = "Всего", example = "107374182400", type = "integer")
        long totalBytes,

        @Schema(description = "Свободно", example = "69793218560", type = "integer")
        long freeBytes) {
}
