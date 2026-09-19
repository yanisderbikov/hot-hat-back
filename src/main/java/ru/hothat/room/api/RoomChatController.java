package ru.hothat.room.api;

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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.room.api.dto.EditRoomChatMessageRequestDTO;
import ru.hothat.room.api.dto.EditedRoomChatMessageResponseDTO;
import ru.hothat.room.api.dto.PostRoomChatImageRequestDTO;
import ru.hothat.room.api.dto.PostRoomChatMessageRequestDTO;
import ru.hothat.room.api.dto.RoomChatImageResponseDTO;
import ru.hothat.room.api.dto.RoomChatMessageResponseDTO;
import ru.hothat.room.api.dto.RoomChatPageResponseDTO;
import ru.hothat.room.api.dto.RoomChatQueryDTO;
import ru.hothat.room.usecase.EditRoomChatMessageUseCase;
import ru.hothat.room.usecase.PostRoomChatImageUseCase;
import ru.hothat.room.usecase.PostRoomChatMessageUseCase;
import ru.hothat.room.usecase.ReadRoomChatUseCase;

/**
 * Чат внутри комнаты.
 *
 * <p>Заменяет живую подписку на {@code rooms/{id}/chat}
 * ({@code app-core.js:8795}) и прямую запись сообщений ({@code :8478},
 * {@code :8610}). Подписка тянула ленту целиком — вместе со встроенными
 * фотографиями по сто с лишним килобайт, — а запись позволяла подписаться в
 * чате комнаты любым именем: путь {@code rooms/…/chat} в проверке прав пуст
 * (находка A1).
 *
 * <p>Отправка разрезана на два адреса. Раньше это был один вызов, а что именно
 * отправляют, решал ключ {@code attachment} внутри тела: у текста обязателен
 * текст, у фотографии — байты и размеры, и два набора обязательных полей одной
 * схемой не описываются.
 *
 * <p>Уровень прав у класса один: участник либо зритель комнаты. Авторство при
 * правке — инвариант сценария: он требует чтения самой строки, и предикат
 * уровня класса читал бы её вторично (§7.4 плана).
 *
 * <p>Старые пути пока живы: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/room/{roomId}")
@PreAuthorize("hasRole('USER')")
@Tag(name = "Room · чат", description = "Сообщения чата комнаты")
@SecurityRequirement(name = "Bearer")
public class RoomChatController {

    private final ReadRoomChatUseCase readChat;
    private final PostRoomChatMessageUseCase postMessage;
    private final PostRoomChatImageUseCase postImage;
    private final EditRoomChatMessageUseCase editMessage;

    @Operation(summary = "Прочитать чат комнаты",
            description = "Сообщения от старых к новым — в порядке показа. Курсор ведёт вглубь "
                    + "истории и считается по моменту отправки, а не по смещению: в чате идущей "
                    + "партии смещение съезжало бы.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Страница чата"),
            @ApiResponse(responseCode = "403", description = "ROOM_MEMBER_ONLY: вы не в этой комнате",
                    content = @Content)})
    @GetMapping("/chat-messages")
    public ResponseEntity<RoomChatPageResponseDTO> history(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Valid @ParameterObject RoomChatQueryDTO query) {
        return ResponseEntity.ok(readChat.run(user, roomId, query));
    }

    @Operation(summary = "Написать в чат комнаты",
            description = "Автора, его имя и место ставит сервер. Имя — снимок на момент отправки: "
                    + "сменивший ник посреди партии не переписывает уже сказанное.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Сообщение создано"),
            @ApiResponse(responseCode = "403", description = "ROOM_MEMBER_ONLY: вы не в этой комнате",
                    content = @Content)})
    @PostMapping("/chat-messages")
    public ResponseEntity<RoomChatMessageResponseDTO> send(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Valid @RequestBody PostRoomChatMessageRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postMessage.run(user, roomId, request));
    }

    @Operation(summary = "Отправить фотографию в чат комнаты",
            description = "Картинка едет прямо в сообщении data-URL'ом: отдельного хранилища у "
                    + "чатовых фото нет, поэтому объём ограничен, а сжимает её отправитель.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Сообщение с фотографией создано"),
            @ApiResponse(responseCode = "403", description = "ROOM_MEMBER_ONLY: вы не в этой комнате",
                    content = @Content)})
    @PostMapping("/chat-images")
    public ResponseEntity<RoomChatImageResponseDTO> sendImage(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Valid @RequestBody PostRoomChatImageRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postImage.run(user, roomId, request));
    }

    @Operation(summary = "Исправить своё сообщение",
            description = "Правится только текст: подменить уже показанную соседям фотографию "
                    + "нельзя. Отметки «исправлено» хранилище не держит, и в ответе её нет.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сообщение исправлено"),
            @ApiResponse(responseCode = "403", description = "NOT_CHAT_AUTHOR: сообщение не ваше",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "CHAT_MESSAGE_NOT_FOUND: сообщения нет",
                    content = @Content),
            @ApiResponse(responseCode = "422", description = "CHAT_IMAGE_NOT_EDITABLE: у сообщения "
                    + "с фотографией править нечего", content = @Content)})
    @PatchMapping("/chat-messages/{messageId}")
    public ResponseEntity<EditedRoomChatMessageResponseDTO> edit(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Сообщение внутри комнаты", example = "chat-9f3a2b1c7d8e0f4a")
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,120}$", message = "Некорректное сообщение.")
            String messageId,
            @Valid @RequestBody EditRoomChatMessageRequestDTO request) {
        return ResponseEntity.ok(editMessage.run(user, roomId, messageId, request));
    }
}
