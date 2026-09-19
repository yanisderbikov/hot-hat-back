package ru.hothat.conference.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.conference.api.dto.ConferenceResponseDTO;
import ru.hothat.conference.api.dto.ConferenceVideoTokenResponseDTO;
import ru.hothat.conference.usecase.CreateConferenceUseCase;
import ru.hothat.conference.usecase.GetConferenceUseCase;
import ru.hothat.conference.usecase.IssueConferenceVideoTokenUseCase;
import ru.hothat.conference.usecase.LeaveConferenceUseCase;
import ru.hothat.config.HotHatUser;

/**
 * Видео-чат глазами участника: завести, посмотреть, войти в звонок, выйти.
 *
 * <p>Видео-чат — созвон до шестнадцати друзей без запуска игры. Возможность
 * придумана в ветке dev фронтенда: там она жила на {@code POST /api/token}
 * с полем {@code conference_action}, где одно тело с тринадцатью значениями
 * строки выбирало между созданием, чтением, приглашением, ответом, выходом,
 * выгоном, чатом и токеном. Здесь у каждого действия свой адрес и свой
 * уровень прав: этот класс — операции участника, хозяйские — в
 * {@link ConferenceHostController}, приглашения — в
 * {@link ConferenceInviteController}, чат — в {@link ConferenceChatController}.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/conference")
@PreAuthorize("hasRole('USER')")
@Tag(name = "Conference · видео-чат", description = "Созвон с друзьями без игры")
@SecurityRequirement(name = "Bearer")
public class ConferenceController {

    /** Форма идентификатора видео-чата; та же, что у {@code ConferenceRules.ID}. */
    static final String ID_PATTERN = "^vc-[a-f0-9]{16}$";

    private final CreateConferenceUseCase createConference;
    private final GetConferenceUseCase getConference;
    private final IssueConferenceVideoTokenUseCase issueVideoToken;
    private final LeaveConferenceUseCase leaveConference;

    @Operation(summary = "Завести видео-чат",
            description = "Создающий становится хозяином и первым участником. Видео-чат живёт двенадцать "
                    + "часов и закрывается сам.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Видео-чат заведён")})
    @PostMapping
    public ResponseEntity<ConferenceResponseDTO> create(@AuthenticationPrincipal HotHatUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createConference.run(user));
    }

    @Operation(summary = "Показать видео-чат",
            description = "Состав и приглашённые. Видят только участники: приглашённому до ответа "
                    + "видео-чат не показывается.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Видео-чат"),
            @ApiResponse(responseCode = "403", description = "CONFERENCE_INVITE_REQUIRED: вы не участник",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "CONFERENCE_NOT_FOUND: видео-чата нет",
                    content = @Content),
            @ApiResponse(responseCode = "410", description = "CONFERENCE_CLOSED: срок вышел",
                    content = @Content)})
    @GetMapping("/{conferenceId}")
    public ResponseEntity<ConferenceResponseDTO> get(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Видео-чат", example = "vc-0f3a9c1d7b2e5480")
            @PathVariable @Pattern(regexp = ID_PATTERN, message = "Некорректный видео-чат.")
            String conferenceId) {
        return ResponseEntity.ok(getConference.run(user, conferenceId));
    }

    @Operation(summary = "Взять видеотокен",
            description = "Пропуск в звонок для участника: под именем из карточки игрока, с правом "
                    + "показывать себя и видеть остальных. Живёт четыре часа, обновляется повторным "
                    + "вызовом.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Токен выдан"),
            @ApiResponse(responseCode = "403", description = "CONFERENCE_INVITE_REQUIRED: вы не участник",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "CONFERENCE_NOT_FOUND: видео-чата нет",
                    content = @Content),
            @ApiResponse(responseCode = "410", description = "CONFERENCE_CLOSED: срок вышел",
                    content = @Content),
            @ApiResponse(responseCode = "503", description = "LIVEKIT_NOT_CONFIGURED: видеоузел не настроен",
                    content = @Content)})
    @PostMapping("/{conferenceId}/video-token")
    public ResponseEntity<ConferenceVideoTokenResponseDTO> videoToken(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Видео-чат", example = "vc-0f3a9c1d7b2e5480")
            @PathVariable @Pattern(regexp = ID_PATTERN, message = "Некорректный видео-чат.")
            String conferenceId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(issueVideoToken.run(user, conferenceId));
    }

    @Operation(summary = "Выйти из видео-чата",
            description = "Снимает своё участие; вернуться можно только по новому приглашению. Хозяин "
                    + "не выходит — для него это просто отключение от звонка. Повторный выход так же "
                    + "успешен, как первый.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Вы больше не участник"),
            @ApiResponse(responseCode = "404", description = "CONFERENCE_NOT_FOUND: видео-чата нет",
                    content = @Content),
            @ApiResponse(responseCode = "410", description = "CONFERENCE_CLOSED: срок вышел",
                    content = @Content)})
    @DeleteMapping("/{conferenceId}/members/me")
    public ResponseEntity<Void> leave(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Видео-чат", example = "vc-0f3a9c1d7b2e5480")
            @PathVariable @Pattern(regexp = ID_PATTERN, message = "Некорректный видео-чат.")
            String conferenceId) {
        leaveConference.run(user, conferenceId);
        return ResponseEntity.noContent().build();
    }
}
