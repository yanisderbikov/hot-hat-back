package ru.hothat.room.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.room.api.dto.DeviceStateResponseDTO;
import ru.hothat.room.api.dto.IssuePlayerVideoTokenRequestDTO;
import ru.hothat.room.api.dto.LeftRoomResponseDTO;
import ru.hothat.room.api.dto.PlayerVideoTokenResponseDTO;
import ru.hothat.room.api.dto.ReportDeviceStateRequestDTO;
import ru.hothat.room.api.dto.RoomSeatHeartbeatResponseDTO;
import ru.hothat.room.usecase.IssuePlayerVideoTokenUseCase;
import ru.hothat.room.usecase.LeaveRoomUseCase;
import ru.hothat.room.usecase.ReportDeviceStateUseCase;
import ru.hothat.room.usecase.TouchRoomSeatUseCase;

/**
 * Собственное место игрока в комнате.
 *
 * <p>Заменяет четыре прямые записи браузера: удаление своего места и уборку
 * комнаты следом ({@code app-core.js:12290}, {@code :1045}), состояние камеры
 * и микрофона ({@code :5093}, {@code livekit.js:3175}), отметку присутствия
 * ({@code :8333}) и {@code POST /api/token} за игрока ({@code livekit.js:541}).
 *
 * <p>Класс отделён от входа не по предмету, а по уровню прав: там игрок ещё
 * снаружи, здесь распоряжается уже занятым местом. Отсюда и {@code players/me}
 * в пути: чужое место не трогают, и адреса для этого не существует — кроме
 * хозяйского удаления, у которого свой класс и своё право.
 *
 * <p>Старые пути пока живы: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/room/{roomId}/players/me")
@PreAuthorize("hasRole('USER')")
@Tag(name = "Room · моё место", description = "Место игрока: выход, устройства, присутствие, видео")
@SecurityRequirement(name = "Bearer")
public class MyRoomSeatController {

    private final LeaveRoomUseCase leaveRoom;
    private final ReportDeviceStateUseCase reportDeviceState;
    private final TouchRoomSeatUseCase touchRoomSeat;
    private final IssuePlayerVideoTokenUseCase issuePlayerVideoToken;

    @Operation(summary = "Выйти из комнаты",
            description = "Снимает место, вычёркивает из команды и из состава идущей партии, а "
                    + "опустевшую комнату убирает тем же вызовом. Раньше уборку заказывал браузер "
                    + "отдельным запросом, и закрытая мимо обработчика вкладка оставляла комнату "
                    + "висеть в витрине.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Вышли"),
            @ApiResponse(responseCode = "403", description = "ROOM_MEMBER_ONLY: вы не в этой комнате",
                    content = @Content)})
    @DeleteMapping
    public ResponseEntity<LeftRoomResponseDTO> leave(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId) {
        return ResponseEntity.ok(leaveRoom.run(user, roomId));
    }

    @Operation(summary = "Сообщить о камере и микрофоне",
            description = "Оба флага приходят вместе: половина состояния показала бы соседям по "
                    + "столу то, чего человек не включал. Отметка времени в ответе серверная — по "
                    + "ней решают, чьё состояние свежее.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Состояние записано"),
            @ApiResponse(responseCode = "403", description = "ROOM_MEMBER_ONLY: вы не в этой комнате",
                    content = @Content)})
    @PutMapping("/devices")
    public ResponseEntity<DeviceStateResponseDTO> devices(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Valid @RequestBody ReportDeviceStateRequestDTO request) {
        return ResponseEntity.ok(reportDeviceState.run(user, roomId, request));
    }

    @Operation(summary = "Отметить присутствие",
            description = "Только отметка и ничего больше: ни передачи хозяйства, ни уборки, ни "
                    + "сверки состава — у каждого из этих действий свой адрес. В ответе серверное "
                    + "время: разница с клиентским и есть поправка часов.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отметка принята"),
            @ApiResponse(responseCode = "403", description = "ROOM_MEMBER_ONLY: вы не в этой комнате",
                    content = @Content)})
    @PutMapping("/heartbeat")
    public ResponseEntity<RoomSeatHeartbeatResponseDTO> heartbeat(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId) {
        return ResponseEntity.ok(touchRoomSeat.run(user, roomId));
    }

    @Operation(summary = "Получить видеотокен игрока",
            description = "Токен с правом публиковать дорожки, адрес видеоузла и учётка TURN, если "
                    + "он настроен. Живёт два часа и обновляется повторным вызовом.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Токен выдан"),
            @ApiResponse(responseCode = "403", description = "ROOM_MEMBER_ONLY: вы не в этой комнате; "
                    + "RANKED_DIVISION_MISMATCH, ROOM_DIVISION_MISMATCH: комната играет в другом "
                    + "дивизионе", content = @Content),
            @ApiResponse(responseCode = "410", description = "ROOM_CLOSED_BY_ADMIN: комната закрыта",
                    content = @Content),
            @ApiResponse(responseCode = "503", description = "LIVEKIT_NOT_CONFIGURED: видеосвязь не "
                    + "настроена на сервере", content = @Content)})
    @PostMapping("/video-token")
    public ResponseEntity<PlayerVideoTokenResponseDTO> videoToken(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Valid @RequestBody(required = false) IssuePlayerVideoTokenRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(issuePlayerVideoToken.run(user, roomId, request));
    }
}
