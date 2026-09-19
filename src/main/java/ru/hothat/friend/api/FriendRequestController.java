package ru.hothat.friend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.friend.api.dto.IncomingFriendRequestsResponseDTO;
import ru.hothat.friend.api.dto.InviteFriendByNicknameRequestDTO;
import ru.hothat.friend.api.dto.InviteFriendByUidRequestDTO;
import ru.hothat.friend.api.dto.OutgoingFriendRequestsResponseDTO;
import ru.hothat.friend.api.dto.SentFriendRequestByUidResponseDTO;
import ru.hothat.friend.api.dto.SentFriendRequestResponseDTO;
import ru.hothat.friend.usecase.InviteFriendByNicknameUseCase;
import ru.hothat.friend.usecase.InviteFriendByUidUseCase;
import ru.hothat.friend.usecase.ListIncomingFriendRequestsUseCase;
import ru.hothat.friend.usecase.ListOutgoingFriendRequestsUseCase;

/**
 * Ведёт собственные заявки в друзья.
 *
 * <p>Заменяет {@code POST /api/portal} с действиями {@code add_friend} (одно
 * тело на два разных способа позвать — по нику или по идентификатору, выбор
 * делался по тому, какое поле непустое) и часть действия {@code friends},
 * возившую входящие и исходящие заявки вместе со списком друзей.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/friends/requests")
@Tag(name = "Friends · заявки", description = "Отправка и просмотр заявок в друзья")
@SecurityRequirement(name = "Bearer")
public class FriendRequestController {

    private final InviteFriendByNicknameUseCase inviteByNickname;
    private final InviteFriendByUidUseCase inviteByUid;
    private final ListIncomingFriendRequestsUseCase listIncoming;
    private final ListOutgoingFriendRequestsUseCase listOutgoing;

    @Operation(summary = "Позвать в друзья по нику",
            description = "Способ с экрана друзей: игрока ищут по нику, регистр не важен.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Заявка создана"),
            @ApiResponse(responseCode = "400", description = "FRIEND_SELF: заявка самому себе",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "PLAYER_NOT_FOUND: такого ника нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "ALREADY_FRIENDS или REQUEST_EXISTS",
                    content = @Content)})
    @PostMapping("/by-nickname")
    public ResponseEntity<SentFriendRequestResponseDTO> inviteByNickname(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody InviteFriendByNicknameRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inviteByNickname.run(user, request));
    }

    @Operation(summary = "Позвать в друзья видимого игрока",
            description = "Способ из комнаты и из консоли администратора: адресат уже на экране, "
                    + "искать его по нику незачем.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Заявка создана"),
            @ApiResponse(responseCode = "400", description = "FRIEND_SELF: заявка самому себе",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "PLAYER_NOT_FOUND: такого игрока нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "ALREADY_FRIENDS или REQUEST_EXISTS",
                    content = @Content)})
    @PostMapping("/by-player")
    public ResponseEntity<SentFriendRequestByUidResponseDTO> inviteByPlayer(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody InviteFriendByUidRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inviteByUid.run(user, request));
    }

    @Operation(summary = "Показать входящие заявки",
            description = "Только ждущие ответа: отвеченные заявки экрану заявок не нужны.")
    @GetMapping("/incoming")
    public ResponseEntity<IncomingFriendRequestsResponseDTO> incoming(
            @AuthenticationPrincipal HotHatUser user) {
        return ResponseEntity.ok(listIncoming.run(user));
    }

    @Operation(summary = "Показать исходящие заявки",
            description = "Ждущие ответа и принятые в одном списке: принятыми живёт значок «друзья» "
                    + "в шапке портала.")
    @GetMapping("/outgoing")
    public ResponseEntity<OutgoingFriendRequestsResponseDTO> outgoing(
            @AuthenticationPrincipal HotHatUser user) {
        return ResponseEntity.ok(listOutgoing.run(user));
    }
}
