package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Отметка «съёмка пошла, первый кадр партии снят».
 *
 * <p>Сегодня это мутирующий {@code GET /api/recording-state?started=1}
 * ({@code app-core.js:14121}). Своя пара DTO, а не переиспользованная от
 * готовности: сигналы разные, и первый из них — гейт запуска, а второй нужен
 * только диагностике.
 */
@Schema(description = "Сигнал начала съёмки")
public record SignalRecorderStartedRequestDTO(

        @Schema(description = "Личность рекордера в комнате LiveKit",
                example = "hot-hat-recorder-3f1c9a2b", maxLength = 220, nullable = true)
        @Size(max = 220, message = "Слишком длинная личность рекордера.")
        String livekitIdentity) {
}
