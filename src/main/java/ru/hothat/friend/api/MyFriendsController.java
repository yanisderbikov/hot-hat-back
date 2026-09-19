package ru.hothat.friend.api;

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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.PlayerUid;
import ru.hothat.config.HotHatUser;
import ru.hothat.friend.api.dto.FriendPresenceResponseDTO;
import ru.hothat.friend.api.dto.MyFriendsPageResponseDTO;
import ru.hothat.friend.api.dto.MyFriendsQueryDTO;
import ru.hothat.friend.usecase.ListFriendPresenceUseCase;
import ru.hothat.friend.usecase.ListMyFriendsUseCase;
import ru.hothat.friend.usecase.RemoveFriendUseCase;

/**
 * Ведёт круг друзей текущего игрока.
 *
 * <p>Заменяет часть {@code POST /api/portal} с действиями {@code friends} и
 * {@code remove_friend}, где список друзей приезжал одним куском вместе с
 * входящими и исходящими заявками. Присутствие вынесено отдельным адресом:
 * карточка друга меняется раз в месяц, а «в сети» — каждую минуту, и опрашивать
 * их с одной частотой значит гонять аватары ради одного числа.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/friends")
@Tag(name = "Friends · круг", description = "Круг друзей текущего игрока")
@SecurityRequirement(name = "Bearer")
public class MyFriendsController {

    private final ListMyFriendsUseCase listMyFriends;
    private final ListFriendPresenceUseCase listFriendPresence;
    private final RemoveFriendUseCase removeFriend;

    @Operation(summary = "Показать друзей",
            description = "Карточки друзей: ник, аватар, дивизион и когда игрока видели в последний раз.")
    @GetMapping
    public ResponseEntity<MyFriendsPageResponseDTO> list(@AuthenticationPrincipal HotHatUser user,
                                                         @ParameterObject @Valid MyFriendsQueryDTO query) {
        return ResponseEntity.ok(listMyFriends.run(user, query));
    }

    @Operation(summary = "Показать присутствие друзей",
            description = "Только идентификатор и время последнего появления. Признак online считает "
                    + "сервер и отдаёт своё время: у клиента часы могут уехать.")
    @GetMapping("/presence")
    public ResponseEntity<FriendPresenceResponseDTO> presence(@AuthenticationPrincipal HotHatUser user) {
        return ResponseEntity.ok(listFriendPresence.run(user));
    }

    @Operation(summary = "Убрать из друзей")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Друга больше нет"),
            @ApiResponse(responseCode = "409", description = "TEAMMATE_MUST_REMAIN_FRIEND: напарник по "
                    + "рейтинговой команде уходит только вместе с командой", content = @Content)})
    @DeleteMapping("/{friendUid}")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Идентификатор друга", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
            @PathVariable @PlayerUid
            String friendUid) {
        removeFriend.run(user, friendUid);
        return ResponseEntity.noContent().build();
    }
}
