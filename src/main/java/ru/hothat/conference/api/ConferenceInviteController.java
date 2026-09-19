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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.conference.api.dto.AnsweredConferenceInviteResponseDTO;
import ru.hothat.conference.api.dto.ConferenceInvitesResponseDTO;
import ru.hothat.conference.api.dto.InviteToConferenceRequestDTO;
import ru.hothat.conference.api.dto.SentConferenceInviteResponseDTO;
import ru.hothat.conference.usecase.AnswerConferenceInviteUseCase;
import ru.hothat.conference.usecase.InviteToConferenceUseCase;
import ru.hothat.conference.usecase.ListMyConferenceInvitesUseCase;
import ru.hothat.config.HotHatUser;

/**
 * Приглашения в видео-чат: позвать друга, увидеть свои, ответить.
 *
 * <p>Приглашение висит у друга на любой странице портала, пока он не выберет
 * «Вступить» или «Отклонить», — карточку кормит канал
 * {@code /ws/v2/me/social} тем же списком, что отдаёт здесь {@code GET}.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/conference")
@PreAuthorize("hasRole('USER')")
@Tag(name = "Conference · приглашения", description = "Позвать друга в видео-чат и ответить на приглашение")
@SecurityRequirement(name = "Bearer")
public class ConferenceInviteController {

    private final InviteToConferenceUseCase invite;
    private final ListMyConferenceInvitesUseCase listMine;
    private final AnswerConferenceInviteUseCase answer;

    @Operation(summary = "Показать свои приглашения",
            description = "Ждущие ответа, новые сверху; истёкшие вместе с видео-чатом не показываются.")
    @GetMapping("/invites")
    public ResponseEntity<ConferenceInvitesResponseDTO> mine(@AuthenticationPrincipal HotHatUser user) {
        return ResponseEntity.ok(listMine.run(user));
    }

    @Operation(summary = "Позвать друга",
            description = "Звать может любой участник, только друга. Того, кто ушёл, не выходя (закрыл "
                    + "вкладку), можно позвать снова; того, кто в звонке, — нет.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Исход в поле outcome: приглашение отправлено, "
                    + "уже ждёт ответа или друг уже в звонке"),
            @ApiResponse(responseCode = "400", description = "CONFERENCE_SELF_INVITE: себя не зовут",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "FRIEND_REQUIRED: не друг; "
                    + "CONFERENCE_INVITE_REQUIRED: вы не участник", content = @Content),
            @ApiResponse(responseCode = "404", description = "CONFERENCE_NOT_FOUND: видео-чата нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "CONFERENCE_FULL: шестнадцать мест уже обещаны",
                    content = @Content),
            @ApiResponse(responseCode = "410", description = "CONFERENCE_CLOSED: срок вышел",
                    content = @Content)})
    @PostMapping("/{conferenceId}/invites")
    public ResponseEntity<SentConferenceInviteResponseDTO> invite(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Видео-чат", example = "vc-0f3a9c1d7b2e5480")
            @PathVariable @Pattern(regexp = ConferenceController.ID_PATTERN, message = "Некорректный видео-чат.")
            String conferenceId,
            @Valid @RequestBody InviteToConferenceRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invite.run(user, conferenceId, request));
    }

    @Operation(summary = "Вступить по приглашению",
            description = "Принимает своё ждущее приглашение; в ответе видео-чат, куда вступили.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Вы участник"),
            @ApiResponse(responseCode = "404", description = "CONFERENCE_NOT_FOUND: видео-чата нет; "
                    + "CONFERENCE_INVITE_NOT_FOUND: вас не звали или уже ответили", content = @Content),
            @ApiResponse(responseCode = "409", description = "CONFERENCE_FULL: мест нет", content = @Content),
            @ApiResponse(responseCode = "410", description = "CONFERENCE_CLOSED: срок вышел",
                    content = @Content)})
    @PostMapping("/{conferenceId}/invites/me/acceptance")
    public ResponseEntity<AnsweredConferenceInviteResponseDTO> accept(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Видео-чат", example = "vc-0f3a9c1d7b2e5480")
            @PathVariable @Pattern(regexp = ConferenceController.ID_PATTERN, message = "Некорректный видео-чат.")
            String conferenceId) {
        return ResponseEntity.ok(answer.run(user, conferenceId, true));
    }

    @Operation(summary = "Отклонить приглашение",
            description = "Снимает карточку; позвать снова можно.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Приглашение отклонено"),
            @ApiResponse(responseCode = "404", description = "CONFERENCE_NOT_FOUND: видео-чата нет; "
                    + "CONFERENCE_INVITE_NOT_FOUND: вас не звали или уже ответили", content = @Content),
            @ApiResponse(responseCode = "410", description = "CONFERENCE_CLOSED: срок вышел",
                    content = @Content)})
    @PostMapping("/{conferenceId}/invites/me/rejection")
    public ResponseEntity<AnsweredConferenceInviteResponseDTO> decline(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Видео-чат", example = "vc-0f3a9c1d7b2e5480")
            @PathVariable @Pattern(regexp = ConferenceController.ID_PATTERN, message = "Некорректный видео-чат.")
            String conferenceId) {
        return ResponseEntity.ok(answer.run(user, conferenceId, false));
    }
}
