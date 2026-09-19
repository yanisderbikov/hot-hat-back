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
import ru.hothat.conference.api.dto.ConferenceMessagesResponseDTO;
import ru.hothat.conference.api.dto.ConferenceUploadTicketResponseDTO;
import ru.hothat.conference.api.dto.PostConferenceFileMessageRequestDTO;
import ru.hothat.conference.api.dto.PostConferenceMessageRequestDTO;
import ru.hothat.conference.api.dto.RequestConferenceUploadTicketRequestDTO;
import ru.hothat.conference.api.dto.SentConferenceMessageResponseDTO;
import ru.hothat.conference.usecase.IssueConferenceUploadTicketUseCase;
import ru.hothat.conference.usecase.PostConferenceFileMessageUseCase;
import ru.hothat.conference.usecase.PostConferenceMessageUseCase;
import ru.hothat.conference.usecase.ReadConferenceMessagesUseCase;
import ru.hothat.config.HotHatUser;

/**
 * Чат видео-чата: лента, текст, файлы.
 *
 * <p>Живую ленту несёт канал {@code /ws/v2/conference/{id}} той же формой,
 * что и {@code GET} здесь; {@code GET} остаётся на случай, когда канал
 * отказал и историю добирают по HTTP. Файлы идут мимо бекенда: билет →
 * PUT в хранилище → сообщение с ключом.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/conference/{conferenceId}")
@PreAuthorize("hasRole('USER')")
@Tag(name = "Conference · чат", description = "Сообщения и файлы видео-чата")
@SecurityRequirement(name = "Bearer")
public class ConferenceChatController {

    private final ReadConferenceMessagesUseCase readMessages;
    private final PostConferenceMessageUseCase postMessage;
    private final PostConferenceFileMessageUseCase postFileMessage;
    private final IssueConferenceUploadTicketUseCase issueUploadTicket;

    @Operation(summary = "Показать ленту",
            description = "Последние сто двадцать сообщений, старые сверху; ссылки на вложения подписаны "
                    + "на два часа.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Лента"),
            @ApiResponse(responseCode = "403", description = "CONFERENCE_INVITE_REQUIRED: вы не участник",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "CONFERENCE_NOT_FOUND: видео-чата нет",
                    content = @Content),
            @ApiResponse(responseCode = "410", description = "CONFERENCE_CLOSED: срок вышел",
                    content = @Content)})
    @GetMapping("/messages")
    public ResponseEntity<ConferenceMessagesResponseDTO> messages(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Видео-чат", example = "vc-0f3a9c1d7b2e5480")
            @PathVariable @Pattern(regexp = ConferenceController.ID_PATTERN, message = "Некорректный видео-чат.")
            String conferenceId) {
        return ResponseEntity.ok(readMessages.run(user, conferenceId));
    }

    @Operation(summary = "Написать в чат")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Сообщение записано"),
            @ApiResponse(responseCode = "400", description = "CONFERENCE_CHAT_EMPTY: пустое сообщение",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "CONFERENCE_INVITE_REQUIRED: вы не участник",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "CONFERENCE_NOT_FOUND: видео-чата нет",
                    content = @Content),
            @ApiResponse(responseCode = "410", description = "CONFERENCE_CLOSED: срок вышел",
                    content = @Content)})
    @PostMapping("/messages")
    public ResponseEntity<SentConferenceMessageResponseDTO> post(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Видео-чат", example = "vc-0f3a9c1d7b2e5480")
            @PathVariable @Pattern(regexp = ConferenceController.ID_PATTERN, message = "Некорректный видео-чат.")
            String conferenceId,
            @Valid @RequestBody PostConferenceMessageRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postMessage.run(user, conferenceId, request));
    }

    @Operation(summary = "Приложить файл",
            description = "Файл уже лежит в хранилище по билету; здесь называют его ключ. Ключ обязан "
                    + "лежать в папке этого видео-чата и этого отправителя.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Сообщение с файлом записано"),
            @ApiResponse(responseCode = "403", description = "CONFERENCE_FILE_INVALID: ключ не из вашей папки; "
                    + "CONFERENCE_INVITE_REQUIRED: вы не участник", content = @Content),
            @ApiResponse(responseCode = "404", description = "CONFERENCE_NOT_FOUND: видео-чата нет",
                    content = @Content),
            @ApiResponse(responseCode = "410", description = "CONFERENCE_CLOSED: срок вышел",
                    content = @Content)})
    @PostMapping("/file-messages")
    public ResponseEntity<SentConferenceMessageResponseDTO> postFile(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Видео-чат", example = "vc-0f3a9c1d7b2e5480")
            @PathVariable @Pattern(regexp = ConferenceController.ID_PATTERN, message = "Некорректный видео-чат.")
            String conferenceId,
            @Valid @RequestBody PostConferenceFileMessageRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postFileMessage.run(user, conferenceId, request));
    }

    @Operation(summary = "Взять билет на загрузку файла",
            description = "Подписанная ссылка на десять минут, до 25 МБ, любой тип содержимого. Заголовок "
                    + "Content-Type при PUT обязан совпасть с тем, что вернул билет.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Билет выдан"),
            @ApiResponse(responseCode = "403", description = "CONFERENCE_INVITE_REQUIRED: вы не участник",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "CONFERENCE_NOT_FOUND: видео-чата нет",
                    content = @Content),
            @ApiResponse(responseCode = "410", description = "CONFERENCE_CLOSED: срок вышел",
                    content = @Content),
            @ApiResponse(responseCode = "503", description = "S3_MEDIA_STORAGE_NOT_CONFIGURED: хранилище "
                    + "не настроено", content = @Content)})
    @PostMapping("/files/upload-tickets")
    public ResponseEntity<ConferenceUploadTicketResponseDTO> uploadTicket(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Видео-чат", example = "vc-0f3a9c1d7b2e5480")
            @PathVariable @Pattern(regexp = ConferenceController.ID_PATTERN, message = "Некорректный видео-чат.")
            String conferenceId,
            @Valid @RequestBody RequestConferenceUploadTicketRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(issueUploadTicket.run(user, conferenceId, request));
    }
}
