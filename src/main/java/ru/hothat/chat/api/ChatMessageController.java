package ru.hothat.chat.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.chat.api.dto.ChatHistoryPageResponseDTO;
import ru.hothat.chat.api.dto.ChatHistoryQueryDTO;
import ru.hothat.chat.api.dto.SendChatImageRequestDTO;
import ru.hothat.chat.api.dto.SendChatTextRequestDTO;
import ru.hothat.chat.api.dto.SentChatImageResponseDTO;
import ru.hothat.chat.api.dto.SentChatTextResponseDTO;
import ru.hothat.chat.api.dto.ShareRecordingInChatRequestDTO;
import ru.hothat.chat.api.dto.SharedRecordingInChatResponseDTO;
import ru.hothat.chat.usecase.MarkChatReadUseCase;
import ru.hothat.chat.usecase.ReadChatHistoryUseCase;
import ru.hothat.chat.usecase.SendChatImageUseCase;
import ru.hothat.chat.usecase.SendChatTextUseCase;
import ru.hothat.chat.usecase.ShareRecordingInChatUseCase;
import ru.hothat.common.validation.PlayerUid;
import ru.hothat.config.HotHatUser;

/**
 * Сообщения одной личной переписки.
 *
 * <p>Заменяет {@code POST /api/portal} с {@code action} из
 * {@code get_chat}, {@code send_chat}, {@code mark_chat_read} и живую подписку
 * браузера на {@code directChats/{pair}/messages}.
 *
 * <p>Отправка разрезана на три адреса. Раньше это был один вызов, а что именно
 * отправляют, решал ключ {@code attachment.kind} внутри тела: у текста
 * обязателен текст, у фотографии — байты и размер, у записи — идентификатор
 * записи, причём чужой записью поделиться нельзя. Три набора обязательных
 * полей и три разных отказа не описываются одной схемой, поэтому теперь у
 * каждого свой адрес и своя пара DTO.
 *
 * <p>Уровень прав у класса один: переписка разрешена другу и напарнику по
 * рейтинговой команде. Проверка стоит в сценариях — предикат
 * {@code @chatAuthz} из плана живёт в пакете безопасности, которого ещё нет.
 *
 * <p>Старые адреса пока живы: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/chat/{peerUid}")
@Tag(name = "Chat · переписка", description = "Сообщения одной личной переписки")
@SecurityRequirement(name = "Bearer")
public class ChatMessageController {


    private final ReadChatHistoryUseCase readHistory;
    private final SendChatTextUseCase sendText;
    private final SendChatImageUseCase sendImage;
    private final ShareRecordingInChatUseCase shareRecording;
    private final MarkChatReadUseCase markRead;

    @Operation(summary = "Прочитать переписку",
            description = "Последние сообщения от старых к новым — в порядке показа.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сообщения отданы"),
            @ApiResponse(responseCode = "403", description = "FRIEND_REQUIRED: собеседник не друг и не напарник",
                    content = @Content),
            // Тот же код с другим статусом: 409 — переписка с самим собой.
            // Статус достался от старой службы и здесь не переписан, но
            // молчать о нём в спецификации нельзя: клиент его получает.
            @ApiResponse(responseCode = "409", description = "FRIEND_REQUIRED: собеседник — вы сами",
                    content = @Content)})
    @GetMapping("/messages")
    public ResponseEntity<ChatHistoryPageResponseDTO> history(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Собеседник", example = "2c9d4e7aP3lM5nB8vC1xZ0qW6eR4")
            @PathVariable @PlayerUid String peerUid,
            @Valid @ParameterObject ChatHistoryQueryDTO query) {
        return ResponseEntity.ok(readHistory.run(user, peerUid, query));
    }

    @Operation(summary = "Отправить текст",
            description = "Повторные пробелы сервер схлопывает; пустое сообщение отвергается.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Сообщение создано"),
            @ApiResponse(responseCode = "403", description = "FRIEND_REQUIRED: собеседник не друг и не напарник",
                    content = @Content),
            // Тот же код с другим статусом: 409 — переписка с самим собой.
            // Статус достался от старой службы и здесь не переписан, но
            // молчать о нём в спецификации нельзя: клиент его получает.
            @ApiResponse(responseCode = "409", description = "FRIEND_REQUIRED: собеседник — вы сами",
                    content = @Content)})
    @PostMapping("/messages")
    public ResponseEntity<SentChatTextResponseDTO> sendText(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Собеседник", example = "2c9d4e7aP3lM5nB8vC1xZ0qW6eR4")
            @PathVariable @PlayerUid String peerUid,
            @Valid @RequestBody SendChatTextRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sendText.run(user, peerUid, request));
    }

    @Operation(summary = "Отправить фотографию",
            description = "Картинка едет прямо в сообщении data-URL'ом: отдельного хранилища "
                    + "у чатовых фото нет, поэтому объём ограничен.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Сообщение с фотографией создано"),
            @ApiResponse(responseCode = "403", description = "FRIEND_REQUIRED: собеседник не друг и не напарник",
                    content = @Content),
            // Тот же код с другим статусом: 409 — переписка с самим собой.
            // Статус достался от старой службы и здесь не переписан, но
            // молчать о нём в спецификации нельзя: клиент его получает.
            @ApiResponse(responseCode = "409", description = "FRIEND_REQUIRED: собеседник — вы сами",
                    content = @Content)})
    @PostMapping("/image-messages")
    public ResponseEntity<SentChatImageResponseDTO> sendImage(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Собеседник", example = "2c9d4e7aP3lM5nB8vC1xZ0qW6eR4")
            @PathVariable @PlayerUid String peerUid,
            @Valid @RequestBody SendChatImageRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sendImage.run(user, peerUid, request));
    }

    @Operation(summary = "Поделиться записью игры",
            description = "Кроме сообщения открывает собеседнику доступ к самой записи. "
                    + "Поделиться можно только записью, сохранённой вами.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Сообщение с записью создано"),
            @ApiResponse(responseCode = "403", description = "RECORDING_NOT_SAVED: запись сохранена не вами",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "RECORDING_NOT_FOUND: такой записи нет",
                    content = @Content)})
    @PostMapping("/recording-messages")
    public ResponseEntity<SharedRecordingInChatResponseDTO> shareRecording(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Собеседник", example = "2c9d4e7aP3lM5nB8vC1xZ0qW6eR4")
            @PathVariable @PlayerUid String peerUid,
            @Valid @RequestBody ShareRecordingInChatRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shareRecording.run(user, peerUid, request));
    }

    @Operation(summary = "Отметить переписку прочитанной",
            description = "Обнуляет счётчик непрочитанного в этой переписке и уменьшает общий "
                    + "счётчик входящих ровно на столько же.")
    @ApiResponse(responseCode = "204", description = "Отметка поставлена")
    @PutMapping("/read-mark")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Собеседник", example = "2c9d4e7aP3lM5nB8vC1xZ0qW6eR4")
            @PathVariable @PlayerUid String peerUid) {
        markRead.run(user, peerUid);
        return ResponseEntity.noContent().build();
    }
}
