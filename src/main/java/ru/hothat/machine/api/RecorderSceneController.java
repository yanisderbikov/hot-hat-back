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
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.machine.api.dto.RecorderRoomStateResponseDTO;
import ru.hothat.machine.api.dto.RecorderSceneResponseDTO;
import ru.hothat.machine.api.dto.RecorderWordResponseDTO;
import ru.hothat.machine.security.MachineSecurityConfig;
import ru.hothat.machine.usecase.ReadRecorderRoomStateUseCase;
import ru.hothat.machine.usecase.ReadRecorderSceneUseCase;
import ru.hothat.machine.usecase.ReadRecorderWordUseCase;

/**
 * Отдаёт рекордеру состояние снимаемой сцены.
 *
 * <p>Заменяет три режима {@code GET /api/recording-state} — без флагов,
 * {@code ?state=1} и {@code ?word=1}. Все три были чтением и остались чтением;
 * четыре мутирующих режима того же адреса уехали в
 * {@link RecorderProgressController} и стали POST.
 *
 * <p>Три адреса, а не один с флагами, потому что у них разная цена и разная
 * частота: сцену опрашивают четыре раза в секунду, зеркало комнаты — только
 * когда не поднялось живое, а слово — на каждой смене слова.
 *
 * <p>Старый адрес пока жив: страницу записи открывает браузер LiveKit Egress.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/machine/recorder/rooms/{roomId}/games/{gameNumber}")
@Tag(name = "Machine · сцена рекордера", description = "Чтение снимаемой сцены")
@SecurityRequirement(name = MachineSecurityConfig.SECRET_SCHEME)
public class RecorderSceneController {

    private final ReadRecorderSceneUseCase readScene;
    private final ReadRecorderRoomStateUseCase readRoomState;
    private final ReadRecorderWordUseCase readWord;

    @Operation(summary = "Прочитать кадр партии",
            description = "Компактная сцена для отрисовки записи: команды, игроки, счёт, "
                    + "текущее слово и диверсии. Если комната ушла к следующей партии, "
                    + "ответ приходит со state=STALE_GAME и пустыми полями.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Кадр или признак устаревшей партии"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: комнаты нет",
                    content = @Content)})
    @GetMapping("/scene")
    public ResponseEntity<RecorderSceneResponseDTO> scene(
            @Parameter(description = "Комната съёмки")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Номер снимаемой партии")
            @PathVariable @Min(value = 0, message = "Номер партии не может быть отрицательным.") int gameNumber) {
        return ResponseEntity.ok(readScene.run(roomId, gameNumber));
    }

    @Operation(summary = "Прочитать зеркало комнаты",
            description = "Полное состояние комнаты. Запасной путь: страница берёт его, только если "
                    + "живое зеркало по сокету не поднялось. Мешок слов и пул наружу не отдаются.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Зеркало или признак устаревшей партии"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: комнаты нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "RECORDING_DISABLED: запись в комнате выключена",
                    content = @Content)})
    @GetMapping("/room-state")
    public ResponseEntity<RecorderRoomStateResponseDTO> roomState(
            @Parameter(description = "Комната съёмки")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Номер снимаемой партии")
            @PathVariable @Min(value = 0, message = "Номер партии не может быть отрицательным.") int gameNumber) {
        return ResponseEntity.ok(readRoomState.run(roomId, gameNumber));
    }

    @Operation(summary = "Прочитать слово текущего хода",
            description = "Запись намеренно показывает слово, которое объясняют. Прогрев здесь "
                    + "не принимается: до старта партии слова ещё нет.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Слово или признак устаревшей партии"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: комнаты нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "RECORDING_DISABLED: запись в комнате выключена",
                    content = @Content)})
    @GetMapping("/current-word")
    public ResponseEntity<RecorderWordResponseDTO> currentWord(
            @Parameter(description = "Комната съёмки")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Номер снимаемой партии")
            @PathVariable @Min(value = 0, message = "Номер партии не может быть отрицательным.") int gameNumber) {
        return ResponseEntity.ok(readWord.run(roomId, gameNumber));
    }
}
