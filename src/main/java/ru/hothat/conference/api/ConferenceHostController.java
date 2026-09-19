package ru.hothat.conference.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
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
import ru.hothat.common.validation.PlayerUid;
import ru.hothat.conference.api.dto.ConferenceResponseDTO;
import ru.hothat.conference.api.dto.CreateConferenceGameRoomRequestDTO;
import ru.hothat.conference.usecase.CreateConferenceGameRoomUseCase;
import ru.hothat.conference.usecase.RemoveConferenceMemberUseCase;
import ru.hothat.config.HotHatUser;

/**
 * Хозяйские операции видео-чата: выгнать участника и завести игровую комнату.
 *
 * <p>Отдельный класс от операций участника — по уровню прав: здесь
 * распоряжается один человек составом всех остальных.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/conference/{conferenceId}")
@PreAuthorize("hasRole('USER')")
@Tag(name = "Conference · хозяин", description = "Состав и игровая комната из видео-чата")
@SecurityRequirement(name = "Bearer")
public class ConferenceHostController {

    private final RemoveConferenceMemberUseCase removeMember;
    private final CreateConferenceGameRoomUseCase createGameRoom;

    @Operation(summary = "Выгнать участника",
            description = "Участник отключается от звонка, и следующий токен ему не выдадут. Тем же "
                    + "адресом отзывается приглашение у того, кто ещё не ответил. Себя хозяин выгнать "
                    + "не может.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Состав после исключения"),
            @ApiResponse(responseCode = "403", description = "CONFERENCE_HOST_ONLY: вы не хозяин; "
                    + "CONFERENCE_INVITE_REQUIRED: вы не участник", content = @Content),
            @ApiResponse(responseCode = "404", description = "CONFERENCE_NOT_FOUND: видео-чата нет; "
                    + "CONFERENCE_PARTICIPANT_NOT_FOUND: такого в составе нет", content = @Content),
            @ApiResponse(responseCode = "409", description = "CONFERENCE_HOST_PROTECTED: хозяина не выгоняют",
                    content = @Content),
            @ApiResponse(responseCode = "410", description = "CONFERENCE_CLOSED: срок вышел",
                    content = @Content)})
    @DeleteMapping("/members/{uid}")
    public ResponseEntity<ConferenceResponseDTO> remove(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Видео-чат", example = "vc-0f3a9c1d7b2e5480")
            @PathVariable @Pattern(regexp = ConferenceController.ID_PATTERN, message = "Некорректный видео-чат.")
            String conferenceId,
            @Parameter(description = "Кого выгнать", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
            @PathVariable @PlayerUid String uid) {
        return ResponseEntity.ok(removeMember.run(user, conferenceId, uid));
    }

    @Operation(summary = "Завести игровую комнату этим составом",
            description = "Приватная комната под названных участников; хозяин садится первым, остальные "
                    + "входят по ссылке из кадра видео-чата. Пока комната набирается, повторный вызов "
                    + "отвечает ею же.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Комната заведена; она в поле gameRoom"),
            @ApiResponse(responseCode = "403", description = "CONFERENCE_HOST_ONLY: вы не хозяин; "
                    + "CONFERENCE_INVITE_REQUIRED: вы не участник", content = @Content),
            @ApiResponse(responseCode = "404", description = "CONFERENCE_NOT_FOUND: видео-чата нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "CONFERENCE_GAME_TOO_MANY: за стол садятся не "
                    + "больше десяти; DEFAULT_LOADOUT_REQUIRED: у хозяина не заряжены пять мемов",
                    content = @Content),
            @ApiResponse(responseCode = "410", description = "CONFERENCE_CLOSED: срок вышел",
                    content = @Content)})
    @PostMapping("/game-room")
    public ResponseEntity<ConferenceResponseDTO> gameRoom(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Видео-чат", example = "vc-0f3a9c1d7b2e5480")
            @PathVariable @Pattern(regexp = ConferenceController.ID_PATTERN, message = "Некорректный видео-чат.")
            String conferenceId,
            @Valid @RequestBody CreateConferenceGameRoomRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createGameRoom.run(user, conferenceId, request));
    }
}
