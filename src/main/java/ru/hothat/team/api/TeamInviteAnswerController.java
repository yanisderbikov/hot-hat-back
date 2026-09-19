package ru.hothat.team.api;

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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.team.api.dto.AcceptedTeamInviteResponseDTO;
import ru.hothat.team.usecase.AcceptTeamInviteUseCase;
import ru.hothat.team.usecase.DeclineTeamInviteUseCase;

/**
 * Разрешает судьбу приглашения в команду.
 *
 * <p>Заменяет {@code POST /api/portal} с действиями {@code accept_team} и
 * {@code decline_team}, которые за одним методом {@code answerInvite(…, boolean)}
 * прятали два несовместимых исхода. Два исхода — два адреса: согласие делает
 * команду подтверждённой и отвечает, с кем игрок теперь в паре, отказ
 * распускает команду и не отвечает ничем.
 *
 * <p>Класс отделён от списка приглашений не по предмету, а по уровню прав:
 * там игрок читает свой список, здесь распоряжается конкретным приглашением,
 * и право на него принадлежит адресату — чужое и несуществующее приглашение
 * дают один и тот же ответ.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/team/invites/{inviteId}")
@Tag(name = "Team · ответ на приглашение", description = "Принятие и отклонение приглашения в команду")
@SecurityRequirement(name = "Bearer")
public class TeamInviteAnswerController {

    private final AcceptTeamInviteUseCase acceptInvite;
    private final DeclineTeamInviteUseCase declineInvite;

    @Operation(summary = "Принять приглашение",
            description = "Команда становится подтверждённой у обоих участников. Ответ называет "
                    + "напарника: экран показывает уведомление до того, как перечитает команду.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Приглашение принято, команда подтверждена"),
            @ApiResponse(responseCode = "404", description = "INVITE_NOT_FOUND: приглашение адресовано "
                    + "не вам, отозвано или на него уже ответили", content = @Content),
            @ApiResponse(responseCode = "409", description = "ALREADY_IN_TEAM, TEAM_DIVISION_MISMATCH",
                    content = @Content)})
    @PostMapping("/acceptance")
    public ResponseEntity<AcceptedTeamInviteResponseDTO> accept(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Идентификатор приглашения", example = "4b2c8e1a-9f6d-4057-b3c1-8e2a7d9f0c46")
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,80}$", message = "Некорректное приглашение.")
            String inviteId) {
        return ResponseEntity.ok(acceptInvite.run(user, inviteId));
    }

    @Operation(summary = "Отклонить приглашение",
            description = "Команда, которая ждала ответа, распускается вместе с занятым названием, "
                    + "и основатель снова свободен.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Приглашение отклонено, команда распущена"),
            @ApiResponse(responseCode = "404", description = "INVITE_NOT_FOUND: приглашение адресовано "
                    + "не вам, отозвано или на него уже ответили", content = @Content)})
    @PostMapping("/rejection")
    public ResponseEntity<Void> decline(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Идентификатор приглашения", example = "4b2c8e1a-9f6d-4057-b3c1-8e2a7d9f0c46")
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,80}$", message = "Некорректное приглашение.")
            String inviteId) {
        declineInvite.run(user, inviteId);
        return ResponseEntity.noContent().build();
    }
}
