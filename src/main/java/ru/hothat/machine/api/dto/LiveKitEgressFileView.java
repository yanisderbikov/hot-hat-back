package ru.hothat.machine.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Файл, который выгрузил Egress.
 *
 * <p>Имена полей приходят из LiveKit в змеином регистре, поэтому у каждого
 * объявлен псевдоним: контракт здесь чужой, и подстраиваться под него надо
 * явно, а не рассыпать по коду {@code getOrDefault("ended_at", get("endedAt"))},
 * как сегодня в {@code RecordingServiceImpl.applyEgressWebhook}.
 */
@Schema(description = "Файл записи в уведомлении LiveKit")
public record LiveKitEgressFileView(

        @Schema(description = "Длительность записи в наносекундах",
                example = "930000000000", type = "integer", format = "int64", nullable = true)
        Long duration,

        @Schema(description = "Размер файла в байтах",
                example = "184320000", type = "integer", format = "int64", nullable = true)
        Long size,

        @Schema(description = "Путь к объекту в бакете",
                example = "game-recordings/hat-3f1c9a2b7d4e6501/game-3.mp4", nullable = true)
        @JsonAlias({"filename", "file_name"})
        String filename) {
}
