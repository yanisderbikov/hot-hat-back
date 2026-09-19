package ru.hothat.room.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.room.api.dto.SpectatorHeartbeatResponseDTO;
import ru.hothat.room.api.dto.SpectatorVideoTokenResponseDTO;
import ru.hothat.room.usecase.IssueSpectatorVideoTokenUseCase;
import ru.hothat.room.usecase.LeaveSpectatorSeatUseCase;
import ru.hothat.room.usecase.TouchSpectatorSeatUseCase;

/**
 * Собственное место зрителя.
 *
 * <p>Заменяет удаление своей строки зрителя ({@code app-core.js:12423}),
 * отметку присутствия ({@code livekit.js:3161}) и {@code POST /api/token} с
 * ролью зрителя.
 *
 * <p>Класс отделён от игроцкого места не по предмету, а по уровню прав: зритель
 * не сидит за столом, его токен не публикует дорожки, а окно живости у него
 * своё — семь минут против пяти. Одна пара адресов на оба места означала бы,
 * что различие видно только в теле ответа.
 *
 * <p>Старые пути пока живы: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/room/{roomId}/spectators/me")
@PreAuthorize("hasRole('USER')")
@Tag(name = "Room · моё место зрителя", description = "Место зрителя: уход, присутствие, видео")
@SecurityRequirement(name = "Bearer")
public class MySpectatorSeatController {

    private final LeaveSpectatorSeatUseCase leaveSpectatorSeat;
    private final TouchSpectatorSeatUseCase touchSpectatorSeat;
    private final IssueSpectatorVideoTokenUseCase issueSpectatorVideoToken;

    @Operation(summary = "Уйти из зала",
            description = "Снимает зрительское место. Комнату не трогает: зритель её не наполнял "
                    + "и, уходя, не опустошает.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Место снято"),
            @ApiResponse(responseCode = "403", description = "SPECTATOR_ONLY: вы не смотрите эту "
                    + "комнату", content = @Content)})
    @DeleteMapping
    public ResponseEntity<Void> leave(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId) {
        leaveSpectatorSeat.run(user, roomId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Отметить присутствие зрителя",
            description = "Окно живости зрителя — семь минут: его выпадение стоит строки в "
                    + "счётчике над столом, а не места в команде.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отметка принята"),
            @ApiResponse(responseCode = "403", description = "SPECTATOR_ONLY: вы не смотрите эту "
                    + "комнату", content = @Content)})
    @PutMapping("/heartbeat")
    public ResponseEntity<SpectatorHeartbeatResponseDTO> heartbeat(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId) {
        return ResponseEntity.ok(touchSpectatorSeat.run(user, roomId));
    }

    @Operation(summary = "Получить видеотокен зрителя",
            description = "Токен только на приём: публиковать дорожки из зала нельзя.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Токен выдан"),
            @ApiResponse(responseCode = "403", description = "SPECTATOR_ONLY: вы не смотрите эту "
                    + "комнату; PRIVATE_ROOM_NOT_WATCHABLE: комната приватная", content = @Content),
            @ApiResponse(responseCode = "410", description = "ROOM_CLOSED_BY_ADMIN: комната закрыта",
                    content = @Content),
            @ApiResponse(responseCode = "503", description = "LIVEKIT_NOT_CONFIGURED: видеосвязь не "
                    + "настроена на сервере", content = @Content)})
    @PostMapping("/video-token")
    public ResponseEntity<SpectatorVideoTokenResponseDTO> videoToken(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(issueSpectatorVideoToken.run(user, roomId));
    }
}
