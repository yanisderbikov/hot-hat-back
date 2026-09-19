package ru.hothat.recording.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.recording.api.dto.FinishedRecordingResponseDTO;
import ru.hothat.recording.api.dto.RecorderReadinessResponseDTO;
import ru.hothat.recording.api.dto.RecordingSessionResponseDTO;
import ru.hothat.recording.api.dto.RecordingStatusResponseDTO;
import ru.hothat.recording.usecase.FinishRecordingUseCase;
import ru.hothat.recording.usecase.GetRecorderReadinessUseCase;
import ru.hothat.recording.usecase.GetRecordingStatusUseCase;
import ru.hothat.recording.usecase.StartRecordingUseCase;

/**
 * Запись одной партии комнаты — со стороны игрока.
 *
 * <p>Заменяет четыре действия {@code POST /api/recordings}: {@code start}
 * ({@code app-core.js:8765}, {@code :12974}), {@code finish} ({@code :8788},
 * {@code :13000}, {@code :13025}), {@code ready_status} ({@code :12985}) и
 * {@code status} ({@code :11622}).
 *
 * <p><b>Где проходит граница с {@code ru.hothat.machine}.</b> Съёмкой заняты
 * двое, и они не заменяют друг друга. Здесь — <i>заказчик</i>: игрок и хозяин
 * комнаты просят начать съёмку, просят остановить и спрашивают, готов ли
 * рекордер. Там, в {@code /api/v2/machine/recorder/**}, — <i>исполнитель</i>:
 * headless-браузер LiveKit Egress открывает сессию, читает сцену и текущее
 * слово, шлёт сигналы {@code ready} и {@code started} и сам закрывает запись
 * по концу церемонии. Разные учётки (Bearer игрока против подписи машины),
 * разные права, разные адреса — и ни одной общей операции: игрок не может
 * отчитаться о готовности, машина не может попросить начать съёмку.
 *
 * <p>Одно место выглядит пересечением и им не является. Готовность рекордера
 * ставит машина сигналом {@code ready-signal}, а читают её здесь: это не
 * дубль, а две стороны одной отметки — кто пишет и кто ждёт.
 *
 * <p>Запись адресуется парой «комната и номер партии», а не своим
 * идентификатором: в момент вопроса экран знает только их, а идентификатор
 * записи — это как раз то, что он хочет узнать (сегодня он склеивает его
 * сам). За вечер в одной комнате партий несколько, поэтому номер обязателен.
 *
 * <p>Уровень прав у класса один: участник этой партии в этой комнате.
 * Проверки пока стоят предусловиями в движке — {@code PLAYER_NOT_FOUND} у
 * съёмки, {@code RECORDING_PARTICIPANT_ONLY} у чтения; в плане им отвечает
 * {@code @roomAuthz.isMember}, и они переедут в предикат вместе с остальными
 * предикатами комнаты.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на этот маршрут отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/recording/rooms/{roomId}/games/{gameNumber}")
@Tag(name = "Recording · партия", description = "Запись одной партии комнаты")
@SecurityRequirement(name = "Bearer")
public class MatchRecordingController {

    private final StartRecordingUseCase startRecording;
    private final FinishRecordingUseCase finishRecording;
    private final GetRecorderReadinessUseCase getRecorderReadiness;
    private final GetRecordingStatusUseCase getRecordingStatus;

    @Operation(summary = "Начать запись партии",
            description = "Поднимает задание LiveKit Egress на эту партию. Отвечает 202, а не 201: "
                    + "к моменту ответа рекордер только поднимается, и снимать он ещё не начал — "
                    + "дожидаться его полагается адресом готовности. Повторный вызов и гонка двух "
                    + "вкладок безопасны: второго задания на одну партию не появится. "
                    + "Выключенная запись и закрытая комната — не ошибка, а исход в поле outcome.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Заявка на съёмку принята"),
            @ApiResponse(responseCode = "403", description = "PLAYER_NOT_FOUND: вы не в этой комнате",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: комнаты нет", content = @Content),
            @ApiResponse(responseCode = "409", description = "RECORDING_GAME_NUMBER_MISMATCH: комната уже "
                    + "перешла к другой партии", content = @Content),
            @ApiResponse(responseCode = "503", description = "S3_RECORDING_STORAGE_NOT_CONFIGURED или "
                    + "LIVEKIT_NOT_CONFIGURED: складывать запись некуда либо снимать нечем",
                    content = @Content)})
    @PostMapping
    public ResponseEntity<RecordingSessionResponseDTO> start(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Номер партии внутри комнаты. В фазе setup это следующая "
                    + "партия, в идущей игре — текущая", example = "3")
            @PathVariable
            @Min(value = 0, message = "Некорректный номер партии.")
            @Max(value = 9999, message = "Некорректный номер партии.")
            int gameNumber) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(startRecording.run(user, roomId, gameNumber));
    }

    @Operation(summary = "Завершить запись партии",
            description = "Останавливает съёмку и снимает с записи срок ожидания. Идемпотентно: "
                    + "адрес зовут из трёх мест сразу, и любой вызов может прийти вторым. "
                    + "Готового файла в ответе нет — LiveKit собирает и выкладывает его позже.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Съёмка остановлена либо останавливать было нечего"),
            @ApiResponse(responseCode = "403", description = "PLAYER_NOT_FOUND: вы не играли эту партию "
                    + "и не в этой комнате", content = @Content),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: комнаты нет", content = @Content)})
    @PostMapping("/finish")
    public ResponseEntity<FinishedRecordingResponseDTO> finish(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Номер партии внутри комнаты", example = "3")
            @PathVariable
            @Min(value = 0, message = "Некорректный номер партии.")
            @Max(value = 9999, message = "Некорректный номер партии.")
            int gameNumber) {
        return ResponseEntity.ok(finishRecording.run(user, roomId, gameNumber));
    }

    @Operation(summary = "Узнать, готов ли рекордер",
            description = "Дешёвый опрос: читает отметки, которые оставила машинная половина, "
                    + "и никуда не ходит. Партию начинают только по ready=true; status=FAILED "
                    + "значит, что ждать больше нечего.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Готовность рекордера"),
            @ApiResponse(responseCode = "403", description = "RECORDING_PARTICIPANT_ONLY: вы не играли "
                    + "эту партию", content = @Content)})
    @GetMapping("/recorder-readiness")
    public ResponseEntity<RecorderReadinessResponseDTO> recorderReadiness(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Номер партии внутри комнаты", example = "3")
            @PathVariable
            @Min(value = 0, message = "Некорректный номер партии.")
            @Max(value = 9999, message = "Некорректный номер партии.")
            int gameNumber) {
        return ResponseEntity.ok(getRecorderReadiness.run(user, roomId, gameNumber));
    }

    @Operation(summary = "Показать состояние записи партии",
            description = "Одна форма ответа на оба случая: если записи нет, exists=false, "
                    + "а карточка пуста. У идущей записи сервер по дороге подтягивает свежий "
                    + "статус из LiveKit и хранилища.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Состояние записи"),
            @ApiResponse(responseCode = "403", description = "RECORDING_PARTICIPANT_ONLY: вы не играли "
                    + "эту партию", content = @Content)})
    @GetMapping
    public ResponseEntity<RecordingStatusResponseDTO> status(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Номер партии внутри комнаты", example = "3")
            @PathVariable
            @Min(value = 0, message = "Некорректный номер партии.")
            @Max(value = 9999, message = "Некорректный номер партии.")
            int gameNumber) {
        return ResponseEntity.ok(getRecordingStatus.run(user, roomId, gameNumber));
    }
}
