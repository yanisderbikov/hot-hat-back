package ru.hothat.machine.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.machine.api.dto.RecorderSessionResponseDTO;
import ru.hothat.machine.security.MachineSecurityConfig;
import ru.hothat.machine.usecase.OpenRecorderSessionUseCase;

/**
 * Снаряжает рекордер для съёмки партии.
 *
 * <p>Заменяет режим {@code GET /api/recording-state?bootstrap=1}. Это создание
 * сессии съёмки — выдаётся токен, — поэтому POST и 201, а не GET.
 *
 * <p>Комната и номер партии стоят в пути, а не в теле, ровно по одной причине:
 * подпись съёмки считается от этой пары, и фильтр обязан проверить её до того,
 * как тело вообще будет разобрано. Тело здесь не нужно вовсе — личность
 * рекордера в LiveKit к моменту снаряжения ещё не известна.
 *
 * <p>Старый адрес пока жив: его открывает браузер LiveKit Egress, который мы
 * не выкатываем вместе с бекендом.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/machine/recorder/rooms/{roomId}/games/{gameNumber}")
@Tag(name = "Machine · снаряжение рекордера", description = "Открытие сессии съёмки партии")
@SecurityRequirement(name = MachineSecurityConfig.SECRET_SCHEME)
public class RecorderSessionController {

    private final OpenRecorderSessionUseCase openRecorderSession;

    @Operation(summary = "Открыть сессию съёмки",
            description = "Отдаёт токен рекордера, зеркало комнаты и состав. "
                    + "Принимается и прогрев: рекордер поднимается ещё до старта партии, "
                    + "и тогда номер партии на единицу больше текущего.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Сессия открыта"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: комнаты нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "RECORDING_GAME_NUMBER_MISMATCH: комната ушла "
                    + "к другой партии; RECORDING_DISABLED: запись в комнате выключена",
                    content = @Content)})
    @PostMapping("/sessions")
    public ResponseEntity<RecorderSessionResponseDTO> open(
            @Parameter(description = "Комната съёмки")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Номер снимаемой партии")
            @PathVariable @Min(value = 0, message = "Номер партии не может быть отрицательным.") int gameNumber) {
        return ResponseEntity.status(HttpStatus.CREATED).body(openRecorderSession.run(roomId, gameNumber));
    }
}
