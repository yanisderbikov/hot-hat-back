package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Отметка «рекордер подключился и готов снимать».
 *
 * <p>Сегодня это мутирующий {@code GET /api/recording-state?ready=1}, а личность
 * рекордера в LiveKit едет там же, в строке запроса, параметром {@code identity}
 * ({@code app-core.js:14059}). Здесь это тело POST-запроса: сигнал меняет
 * состояние, значит GET ему не подходит.
 */
@Schema(description = "Сигнал готовности рекордера")
public record SignalRecorderReadyRequestDTO(

        @Schema(description = "Личность рекордера в комнате LiveKit; по ней запись потом "
                + "отличает свою дорожку от участников",
                example = "hot-hat-recorder-3f1c9a2b", maxLength = 220, nullable = true)
        @Size(max = 220, message = "Слишком длинная личность рекордера.")
        String livekitIdentity) {
}
