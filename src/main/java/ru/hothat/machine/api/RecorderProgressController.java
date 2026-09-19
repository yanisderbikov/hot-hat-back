package ru.hothat.machine.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.machine.api.dto.RecorderCeremonyResponseDTO;
import ru.hothat.machine.api.dto.RecorderReadySignalResponseDTO;
import ru.hothat.machine.api.dto.RecorderStartSignalResponseDTO;
import ru.hothat.machine.api.dto.SignalRecorderReadyRequestDTO;
import ru.hothat.machine.api.dto.SignalRecorderStartedRequestDTO;
import ru.hothat.machine.security.MachineSecurityConfig;
import ru.hothat.machine.usecase.CompleteRecorderCeremonyUseCase;
import ru.hothat.machine.usecase.SignalRecorderReadyUseCase;
import ru.hothat.machine.usecase.SignalRecorderStartedUseCase;

/**
 * Принимает от рекордера отметки хода съёмки.
 *
 * <p>Здесь собраны три режима, которые сегодня меняют состояние на GET:
 * {@code ?ready=1} и {@code ?started=1} пишут в базу и заводят карточку записи,
 * {@code ?ceremony_done=1} останавливает Egress и закрывает файл. Мутирующий
 * GET — это не стилистика: ссылку с такими параметрами повторяет и журнал
 * доступа, и превентивная загрузка прокси, и обычное обновление страницы.
 *
 * <p>Отдельный класс от {@link RecorderSceneController} — потому что у чтения
 * и у сигналов разная ответственность; право у них одно, и это единственная
 * причина, по которой они не разъехались ещё и по адресам.
 *
 * <p>Старый адрес пока жив: страницу записи открывает браузер LiveKit Egress.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/machine/recorder/rooms/{roomId}/games/{gameNumber}")
@Tag(name = "Machine · ход съёмки", description = "Отметки рекордера о ходе съёмки")
@SecurityRequirement(name = MachineSecurityConfig.SECRET_SCHEME)
public class RecorderProgressController {

    private final SignalRecorderReadyUseCase signalReady;
    private final SignalRecorderStartedUseCase signalStarted;
    private final CompleteRecorderCeremonyUseCase completeCeremony;

    @Operation(summary = "Отметить готовность рекордера",
            description = "Рекордер подключился к комнате LiveKit и готов снимать. Хозяин комнаты "
                    + "ждёт этой отметки, чтобы начать партию. Принимается и в прогреве.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Готовность принята"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: комнаты нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "RECORDING_GAME_NUMBER_MISMATCH: комната ушла "
                    + "к другой партии", content = @Content)})
    @PostMapping("/ready-signal")
    public ResponseEntity<RecorderReadySignalResponseDTO> ready(
            @Parameter(description = "Комната съёмки")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Номер снимаемой партии")
            @PathVariable @Min(value = 0, message = "Номер партии не может быть отрицательным.") int gameNumber,
            @Valid @RequestBody(required = false) SignalRecorderReadyRequestDTO request) {
        return ResponseEntity.ok(signalReady.run(roomId, gameNumber, request));
    }

    @Operation(summary = "Отметить начало съёмки",
            description = "Первый кадр партии снят. Партия этой отметки не ждёт: она нужна разбору "
                    + "полётов — по паре «готов» и «пошла» видно, сколько поднимался рекордер.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Начало съёмки принято"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: комнаты нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "RECORDING_GAME_NUMBER_MISMATCH: комната ушла "
                    + "к другой партии", content = @Content)})
    @PostMapping("/start-signal")
    public ResponseEntity<RecorderStartSignalResponseDTO> started(
            @Parameter(description = "Комната съёмки")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Номер снимаемой партии")
            @PathVariable @Min(value = 0, message = "Номер партии не может быть отрицательным.") int gameNumber,
            @Valid @RequestBody(required = false) SignalRecorderStartedRequestDTO request) {
        return ResponseEntity.ok(signalStarted.run(roomId, gameNumber, request));
    }

    @Operation(summary = "Завершить съёмку после церемонии",
            description = "Останавливает Egress и закрывает файл. Требует фазы finished: церемония "
                    + "идёт после конца партии. Повтор безопасен — ответ придёт с "
                    + "outcome=ALREADY_FINISHED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Съёмка завершена или уже была завершена"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: комнаты нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "RECORDING_CEREMONY_NOT_READY: партия ещё не "
                    + "закончена; RECORDING_DISABLED: запись в комнате выключена", content = @Content)})
    @PostMapping("/ceremony-completion")
    public ResponseEntity<RecorderCeremonyResponseDTO> ceremonyCompleted(
            @Parameter(description = "Комната съёмки")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Номер снимаемой партии")
            @PathVariable @Min(value = 0, message = "Номер партии не может быть отрицательным.") int gameNumber) {
        return ResponseEntity.ok(completeCeremony.run(roomId, gameNumber));
    }
}
