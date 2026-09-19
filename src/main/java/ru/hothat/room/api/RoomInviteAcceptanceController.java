package ru.hothat.room.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.room.api.dto.AcceptedRoomInviteResponseDTO;
import ru.hothat.room.usecase.AcceptRoomInviteUseCase;

/**
 * Принятие приглашения в комнату.
 *
 * <p>Заменяет {@code accept_room_invite} ({@code app-core.js:4818}) и
 * забирает у клиента второй шаг: раньше движок заводил место, а браузер следом
 * читал комнату и решал, пускать ли себя. Между двумя шагами умещался старт
 * партии, и человек оказывался с местом в комнате, куда его уже не впустят.
 *
 * <p>Отдельный класс от чтения статусов — по уровню прав: здесь распоряжается
 * один адресат одним приглашением, и право принадлежит ему.
 *
 * <p>{@code acceptance} отдельным сегментом, а не {@code POST} на само
 * приглашение: приглашение — не коллекция, и создаётся здесь именно факт его
 * принятия.
 *
 * <p>Старый путь пока жив: фронтенд переедет на этот маршрут отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/room/invites/{inviteId}")
@PreAuthorize("hasRole('USER')")
@Tag(name = "Room · принятие приглашения", description = "Вход в комнату по приглашению")
@SecurityRequirement(name = "Bearer")
public class RoomInviteAcceptanceController {

    private final AcceptRoomInviteUseCase acceptRoomInvite;

    @Operation(summary = "Принять приглашение",
            description = "Принимает приглашение и сажает в комнату одним вызовом. Допуск тот же, "
                    + "что у входа по идентификатору: приглашение отвечает на вопрос «звали ли "
                    + "меня», а не «можно ли мне сюда».")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Приглашение принято, место занято"),
            @ApiResponse(responseCode = "403", description = "ROOM_INVITE_FORBIDDEN: приглашение не "
                    + "вам; RANKED_DIVISION_MISMATCH, ROOM_DIVISION_MISMATCH: комната играет в "
                    + "другом дивизионе", content = @Content),
            @ApiResponse(responseCode = "404", description = "ROOM_INVITE_NOT_FOUND: приглашения "
                    + "нет; ROOM_NOT_FOUND: комнаты нет", content = @Content),
            @ApiResponse(responseCode = "409", description = "ROOM_INVITE_GAME_STARTED: партия уже "
                    + "началась или сыграна; ROOM_INVITE_UNAVAILABLE: приглашение отозвано; "
                    + "ROOM_FULL: мест нет; DEFAULT_LOADOUT_REQUIRED: не заряжены пять мемов",
                    content = @Content),
            @ApiResponse(responseCode = "410", description = "ROOM_INVITE_EXPIRED: срок действия "
                    + "истёк", content = @Content)})
    @PostMapping("/acceptance")
    public ResponseEntity<AcceptedRoomInviteResponseDTO> accept(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Приглашение в комнату", example = "ri-3f9a2b1c7d8e0f4a")
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,120}$",
                    message = "Некорректное приглашение.")
            String inviteId) {
        return ResponseEntity.ok(acceptRoomInvite.run(user, inviteId));
    }
}
