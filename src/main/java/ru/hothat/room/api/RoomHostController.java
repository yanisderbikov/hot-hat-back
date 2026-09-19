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
import ru.hothat.room.api.dto.ClosedRoomResponseDTO;
import ru.hothat.room.api.dto.HostActivityResponseDTO;
import ru.hothat.room.api.dto.PublicPresenceResponseDTO;
import ru.hothat.room.api.dto.RenameRoomRequestDTO;
import ru.hothat.room.api.dto.ReportHostActivityRequestDTO;
import ru.hothat.room.api.dto.RoomHostResponseDTO;
import ru.hothat.room.api.dto.RoomNameResponseDTO;
import ru.hothat.room.api.dto.RoomSetupResponseDTO;
import ru.hothat.room.api.dto.ResetRoomRequestDTO;
import ru.hothat.room.api.dto.SetTurnDurationRequestDTO;
import ru.hothat.room.api.dto.TransferHostRequestDTO;
import ru.hothat.room.api.dto.TurnDurationResponseDTO;
import ru.hothat.room.usecase.CloseRoomUseCase;
import ru.hothat.room.usecase.RenameRoomUseCase;
import ru.hothat.room.usecase.ReportHostActivityUseCase;
import ru.hothat.room.usecase.ReportRoomPublicPresenceUseCase;
import ru.hothat.room.usecase.ResetRoomUseCase;
import ru.hothat.room.usecase.SetTurnDurationUseCase;
import ru.hothat.room.usecase.TransferHostUseCase;

/**
 * Чем в комнате распоряжается хозяин.
 *
 * <p>Собраны семь действий одного уровня прав. Три из них до сих пор делал
 * браузер прямой записью — имя комнаты ({@code app-core.js:12082}),
 * длительность хода ({@code :12723}) и счётчик игроков для витрины
 * ({@code :11931}), — и все три опирались на пустую ветку {@code rooms/*} в
 * проверке прав (находка A1). Ещё две — возврат к настройкам ({@code :13508},
 * {@code :13570}) — были двумя разными процедурами, сведёнными здесь в один
 * сценарий с флагом глубины.
 *
 * <p>Закрытие комнаты заменяет {@code POST /api/cleanup-rooms?room_id=}
 * ({@code :1048}) — адрес, который не принимал личность вовсе (находка A2) и
 * позволял снести чужую комнату по идентификатору из чата.
 *
 * <p>Передача хозяйства руками живёт здесь, а сторож бездействия — в соседнем
 * классе: его спрашивает как раз не-хозяин, и уровень прав там другой.
 *
 * <p>Старые пути пока живы: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/room/{roomId}")
@PreAuthorize("hasRole('USER')")
@Tag(name = "Room · хозяин", description = "Настройки комнаты и распоряжения хозяина")
@SecurityRequirement(name = "Bearer")
public class RoomHostController {

    private final RenameRoomUseCase renameRoom;
    private final SetTurnDurationUseCase setTurnDuration;
    private final CloseRoomUseCase closeRoom;
    private final ResetRoomUseCase resetRoom;
    private final TransferHostUseCase transferHost;
    private final ReportHostActivityUseCase reportHostActivity;
    private final ReportRoomPublicPresenceUseCase reportPublicPresence;

    @Operation(summary = "Переименовать комнату",
            description = "Имя видно в витрине и уезжает в приглашения, поэтому меняет его один "
                    + "человек. В ответе — записанное имя, а не присланное: сервер обрезает длину "
                    + "и схлопывает пробелы.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Имя записано"),
            @ApiResponse(responseCode = "403", description = "HOST_ONLY: вы не хозяин комнаты",
                    content = @Content)})
    @PutMapping("/name")
    public ResponseEntity<RoomNameResponseDTO> rename(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Valid @RequestBody RenameRoomRequestDTO request) {
        return ResponseEntity.ok(renameRoom.run(user, roomId, request));
    }

    @Operation(summary = "Задать длительность хода",
            description = "Только до начала партии: длительность заморожена вместе с составами, и "
                    + "менять её на ходу значило бы дать одной команде больше времени.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Длительность записана"),
            @ApiResponse(responseCode = "403", description = "HOST_ONLY: вы не хозяин комнаты",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "ROOM_SETUP_ONLY: партия уже началась",
                    content = @Content)})
    @PutMapping("/turn-duration")
    public ResponseEntity<TurnDurationResponseDTO> turnDuration(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Valid @RequestBody SetTurnDurationRequestDTO request) {
        return ResponseEntity.ok(setTurnDuration.run(user, roomId, request));
    }

    @Operation(summary = "Закрыть комнату",
            description = "Если, кроме хозяина, живых людей нет, комната стирается целиком. Если "
                    + "кто-то ещё сидит, она помечается закрытой и остаётся: этим людям надо "
                    + "показать «хозяин закрыл комнату», а не ненайденную комнату.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Комната закрыта"),
            @ApiResponse(responseCode = "403", description = "HOST_ONLY: вы не хозяин комнаты",
                    content = @Content)})
    @DeleteMapping
    public ResponseEntity<ClosedRoomResponseDTO> close(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId) {
        return ResponseEntity.ok(closeRoom.run(user, roomId));
    }

    @Operation(summary = "Вернуть комнату к набору",
            description = "Обнуляет счёт и стирает всё состояние сыгранной партии одной "
                    + "транзакцией. С флагом clearTeams вдобавок распускает команды и выбрасывает "
                    + "сданные слова — это «собираемся заново», а без него «сыграем ещё раз тем же "
                    + "составом».")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Комната вернулась к набору"),
            @ApiResponse(responseCode = "403", description = "HOST_ONLY: вы не хозяин комнаты",
                    content = @Content)})
    @PostMapping("/reset")
    public ResponseEntity<RoomSetupResponseDTO> reset(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Valid @RequestBody(required = false) ResetRoomRequestDTO request) {
        return ResponseEntity.ok(resetRoom.run(user, roomId, request));
    }

    @Operation(summary = "Передать комнату другому",
            description = "Только до начала партии и только живому участнику: отдать комнату тому, "
                    + "чья вкладка закрылась, значит оставить её без хозяина совсем.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Комната передана"),
            @ApiResponse(responseCode = "400", description = "HOST_TRANSFER_TARGET_INVALID: выбран "
                    + "сам передающий", content = @Content),
            @ApiResponse(responseCode = "403", description = "HOST_ONLY: вы не хозяин комнаты",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "PLAYER_NOT_FOUND: такого участника нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "GAME_ALREADY_STARTED: партия идёт; "
                    + "PLAYER_NOT_ACTIVE: участник сейчас неактивен", content = @Content)})
    @PutMapping("/host")
    public ResponseEntity<RoomHostResponseDTO> transfer(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Valid @RequestBody TransferHostRequestDTO request) {
        return ResponseEntity.ok(transferHost.run(user, roomId, request));
    }

    @Operation(summary = "Подтвердить присутствие хозяина",
            description = "Отсчёт бездействия идёт только при полном составе. Порог возвращается "
                    + "ответом: сегодня три минуты вписаны литералом и на сервере, и в двух местах "
                    + "фронтенда, и обратный отсчёт на экране считается по клиентской копии.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отметка принята либо не понадобилась"),
            @ApiResponse(responseCode = "403", description = "HOST_ONLY: вы не хозяин комнаты",
                    content = @Content)})
    @PutMapping("/host/heartbeat")
    public ResponseEntity<HostActivityResponseDTO> hostHeartbeat(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Valid @RequestBody ReportHostActivityRequestDTO request) {
        return ResponseEntity.ok(reportHostActivity.run(user, roomId, request));
    }

    @Operation(summary = "Пересчитать счётчик игроков для витрины",
            description = "Тела у запроса нет: число считает сервер. Присылать ему то, что он и "
                    + "так знает, незачем, а до сих пор это число писал браузер хозяина — и главная "
                    + "рисовала витрину по нему.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Счётчик пересчитан"),
            @ApiResponse(responseCode = "403", description = "HOST_ONLY: вы не хозяин комнаты",
                    content = @Content)})
    @PutMapping("/public-presence")
    public ResponseEntity<PublicPresenceResponseDTO> publicPresence(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId) {
        return ResponseEntity.ok(reportPublicPresence.run(user, roomId));
    }
}
