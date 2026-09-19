package ru.hothat.admin.api;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.admin.api.dto.AdminRoomClosureResponseDTO;
import ru.hothat.admin.api.dto.BanUserRequestDTO;
import ru.hothat.admin.api.dto.BannedUserResponseDTO;
import ru.hothat.admin.api.dto.CloseRoomByAdminRequestDTO;
import ru.hothat.admin.usecase.BanUserUseCase;
import ru.hothat.admin.usecase.CloseRoomByAdminUseCase;
import ru.hothat.admin.usecase.LiftBanUseCase;
import ru.hothat.common.validation.PlayerUid;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;

/**
 * Пресекает нарушения решением администратора.
 *
 * <p>Заменяет три действия {@code POST /api/admin}: {@code ban_user},
 * {@code unban_user} и {@code close_room}. Раньше это было одно тело с полем
 * {@code action}, где идентификатор комнаты не проверялся вовсе — только
 * приводился к нижнему регистру ({@code AdminController:84}).
 *
 * <p>Блокировка — созданный ресурс, поэтому {@code POST …/bans} и 201; снятие
 * — удаление этого ресурса, поэтому {@code DELETE} и 204. Закрытие комнаты —
 * под-ресурс комнаты, а не действие строкой в теле.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin · модерация", description = "Блокировки игроков и закрытие комнат")
@SecurityRequirement(name = "Bearer")
public class AdminModerationController {

    private final BanUserUseCase banUser;
    private final LiftBanUseCase liftBan;
    private final CloseRoomByAdminUseCase closeRoom;

    @Operation(summary = "Заблокировать игрока",
            description = "Заводит блокировку, поднимает поколение токенов и отзывает живые "
                    + "refresh-токены — вход закрывается сразу, а не через пятнадцать минут. "
                    + "Если названа комната, место игрока в ней помечается, а из видеосвязи "
                    + "его выставляют после того, как всё это сохранено.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Блокировка заведена"),
            @ApiResponse(responseCode = "404", description = "BAN_TARGET_NOT_FOUND: такой учётки нет",
                    content = @Content),
            @ApiResponse(responseCode = "422", description = "BAN_SELF_FORBIDDEN: себя заблокировать нельзя",
                    content = @Content)})
    @PostMapping("/bans")
    public ResponseEntity<BannedUserResponseDTO> ban(@AuthenticationPrincipal HotHatUser admin,
                                                     @Valid @RequestBody BanUserRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(banUser.run(admin, request));
    }

    @Operation(summary = "Снять блокировку",
            description = "Поколение токенов поднимается и здесь: возвращать к жизни сессии, "
                    + "открытые до блокировки, незачем — пусть человек войдёт заново.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Блокировки больше нет"),
            @ApiResponse(responseCode = "404", description = "BAN_TARGET_NOT_FOUND: такой учётки нет",
                    content = @Content)})
    @DeleteMapping("/bans/{uid}")
    public ResponseEntity<Void> lift(
            @AuthenticationPrincipal HotHatUser admin,
            @Parameter(description = "Кого разблокировать", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
            @PathVariable @PlayerUid String uid) {
        liftBan.run(admin, uid);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Закрыть комнату",
            description = "Переводит комнату в закрытую фазу и сносит видеокомнату после того, "
                    + "как это сохранено. Повторное закрытие отвечает тем же самым: операция "
                    + "идемпотентна.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Комната закрыта"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: такой комнаты нет",
                    content = @Content)})
    @PostMapping("/rooms/{roomId}/closure")
    public ResponseEntity<AdminRoomClosureResponseDTO> close(
            @AuthenticationPrincipal HotHatUser admin,
            @Parameter(description = "Какую комнату закрыть", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Valid @RequestBody(required = false) CloseRoomByAdminRequestDTO request) {
        return ResponseEntity.ok(closeRoom.run(admin, roomId, request));
    }
}
