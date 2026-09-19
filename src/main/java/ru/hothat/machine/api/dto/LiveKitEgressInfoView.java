package ru.hothat.machine.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

import java.util.List;

/** Состояние выгрузки, как его описывает LiveKit. */
@Schema(description = "Состояние выгрузки в уведомлении LiveKit")
public record LiveKitEgressInfoView(

        @Schema(description = "Идентификатор выгрузки в LiveKit",
                example = "EG_7QhKp2LmN4xR", nullable = true)
        @JsonAlias("egress_id")
        String egressId,

        @Schema(description = "Состояние выгрузки", example = "EGRESS_COMPLETE",
                allowableValues = {"EGRESS_STARTING", "EGRESS_ACTIVE", "EGRESS_ENDING", "EGRESS_COMPLETE",
                        "EGRESS_FAILED", "EGRESS_ABORTED", "EGRESS_LIMIT_REACHED"}, nullable = true)
        String status,

        @Schema(description = "Текст ошибки выгрузки", example = "upload failed", nullable = true)
        String error,

        @Schema(description = "Подробности ошибки", example = "connection reset", nullable = true)
        String details,

        @Schema(description = "Когда выгрузка закончилась, наносекунды эпохи",
                example = "1757068800000000000", type = "integer", format = "int64", nullable = true)
        @JsonAlias("ended_at")
        Long endedAt,

        // Плоский @Schema, а не @ArraySchema: описание из arraySchema springdoc
        // до свойства не доносит — поле уезжало в спецификацию голым массивом,
        // единственным без описания среди пятидесяти. Остальные сорок девять
        // массивов проекта объявлены именно так.
        @Schema(description = "Выгруженные файлы: берём первый")
        @JsonAlias("file_results")
        @Valid
        List<LiveKitEgressFileView> fileResults) {
}
