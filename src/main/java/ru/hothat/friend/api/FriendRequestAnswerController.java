package ru.hothat.friend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.friend.api.dto.AcceptedFriendRequestResponseDTO;
import ru.hothat.friend.usecase.AcceptFriendRequestUseCase;
import ru.hothat.friend.usecase.DeclineFriendRequestUseCase;

/**
 * Разрешает судьбу входящей заявки.
 *
 * <p>Заменяет {@code POST /api/portal} с действием {@code answer_friend}, где
 * согласие и отказ различались булевым полем {@code accept} в теле. Два исхода
 * — два адреса: у них разные последствия (согласие создаёт дружбу, отказ не
 * создаёт ничего) и разные ответы.
 *
 * <p>Класс отделён от {@code FriendRequestController} не по предмету, а по
 * уровню прав: там игрок распоряжается своими заявками, здесь — чужой,
 * присланной ему, и право на неё принадлежит адресату.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/friends/requests/{requestId}")
@Tag(name = "Friends · ответ на заявку", description = "Принятие и отклонение входящей заявки")
@SecurityRequirement(name = "Bearer")
public class FriendRequestAnswerController {

    private final AcceptFriendRequestUseCase acceptRequest;
    private final DeclineFriendRequestUseCase declineRequest;

    @Operation(summary = "Принять заявку",
            description = "Создаёт дружбу и называет нового друга: экран показывает уведомление "
                    + "до того, как перечитает список.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Заявка принята, дружба создана"),
            @ApiResponse(responseCode = "403", description = "Заявка адресована не вам", content = @Content),
            @ApiResponse(responseCode = "404", description = "REQUEST_NOT_FOUND: на заявку уже ответили",
                    content = @Content)})
    @PostMapping("/acceptance")
    public ResponseEntity<AcceptedFriendRequestResponseDTO> accept(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Идентификатор входящей заявки", example = "8123")
            @PathVariable @Positive(message = "Некорректная заявка.") long requestId) {
        return ResponseEntity.ok(acceptRequest.run(user, requestId));
    }

    @Operation(summary = "Отклонить заявку")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Заявка отклонена"),
            @ApiResponse(responseCode = "403", description = "Заявка адресована не вам", content = @Content),
            @ApiResponse(responseCode = "404", description = "REQUEST_NOT_FOUND: на заявку уже ответили",
                    content = @Content)})
    @PostMapping("/rejection")
    public ResponseEntity<Void> decline(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Идентификатор входящей заявки", example = "8123")
            @PathVariable @Positive(message = "Некорректная заявка.") long requestId) {
        declineRequest.run(user, requestId);
        return ResponseEntity.noContent().build();
    }
}
