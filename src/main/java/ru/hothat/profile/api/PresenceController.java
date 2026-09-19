package ru.hothat.profile.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.ActiveRoomResponseDTO;
import ru.hothat.profile.api.dto.PresenceHeartbeatResponseDTO;
import ru.hothat.profile.api.dto.PresenceSummaryResponseDTO;
import ru.hothat.profile.api.dto.SetActiveRoomRequestDTO;
import ru.hothat.profile.usecase.ClearMyActiveRoomUseCase;
import ru.hothat.profile.usecase.CountOnlinePlayersUseCase;
import ru.hothat.profile.usecase.GetMyActiveRoomUseCase;
import ru.hothat.profile.usecase.SetMyActiveRoomUseCase;
import ru.hothat.profile.usecase.TouchMyPresenceUseCase;

/**
 * Ведёт сведения о присутствии игроков в портале.
 *
 * <p>Заменяет действия {@code presence_ping} и {@code presence_summary}, а
 * заодно две прямые записи браузера в {@code users/{uid}.activeRoomId}
 * ({@code app-core.js:setProfileActiveRoom}). Отметка приходит раз в полминуты
 * с каждой открытой вкладки, поэтому это самый частый запрос портала — и самый
 * дешёвый: одна запись по первичному ключу и один счёт.
 *
 * <p>Присутствие «в портале» и присутствие «в комнате» — разные вещи и разные
 * адреса. Первое протухает само через две минуты и говорит только «человек
 * тут». Второе живёт, пока его не снимут, и решает судьбу следующего запуска:
 * по нему игрока возвращают в незаконченную партию.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/profile/presence")
@Tag(name = "Profile · присутствие", description = "Отметка «я здесь», текущая комната и число игроков онлайн")
@SecurityRequirement(name = "Bearer")
public class PresenceController {

    private final TouchMyPresenceUseCase touchMyPresence;
    private final CountOnlinePlayersUseCase countOnlinePlayers;
    private final GetMyActiveRoomUseCase getMyActiveRoom;
    private final SetMyActiveRoomUseCase setMyActiveRoom;
    private final ClearMyActiveRoomUseCase clearMyActiveRoom;

    @Operation(summary = "Отметиться в портале",
            description = "Обновляет время последней активности и возвращает часы сервера: "
                    + "по ним клиент сверяет свои таймеры.")
    @PutMapping("/me")
    public ResponseEntity<PresenceHeartbeatResponseDTO> touch(@AuthenticationPrincipal HotHatUser user) {
        return ResponseEntity.ok(touchMyPresence.run(user));
    }

    @Operation(summary = "Сколько игроков онлайн",
            description = "Онлайн — те, кто отмечался за последние две минуты; не меньше единицы.")
    @GetMapping("/summary")
    public ResponseEntity<PresenceSummaryResponseDTO> summary() {
        return ResponseEntity.ok(countOnlinePlayers.run());
    }

    @Operation(summary = "Где я сейчас играю",
            description = "Метка комнаты, по которой игрок возвращается в идущую партию "
                    + "после перезагрузки вкладки. Пустой roomId — нигде не играю.")
    @ApiResponse(responseCode = "200", description = "Метка прочитана")
    @GetMapping("/me/room")
    public ResponseEntity<ActiveRoomResponseDTO> activeRoom(@AuthenticationPrincipal HotHatUser user) {
        return ResponseEntity.ok(getMyActiveRoom.run(user));
    }

    @Operation(summary = "Отметить комнату, в которой я играю",
            description = "По этой метке игрока возвращают в незаконченную партию при следующем "
                    + "запуске — в том числе с другого устройства, где локальной памяти нет. "
                    + "Метка одна: играть в двух комнатах разом нельзя.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Метка поставлена"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND — такой комнаты нет; "
                    + "метка в никуда вернула бы игрока в пустой экран", content = @Content)})
    @PutMapping("/me/room")
    public ResponseEntity<ActiveRoomResponseDTO> enterRoom(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody SetActiveRoomRequestDTO request) {
        return ResponseEntity.ok(setMyActiveRoom.run(user, request));
    }

    @Operation(summary = "Снять отметку о комнате",
            description = "Конец партии, выход из комнаты или неудачная попытка вернуться. "
                    + "После этого автоматического возвращения в игру не будет.")
    @ApiResponse(responseCode = "204", description = "Метка снята")
    @DeleteMapping("/me/room")
    public ResponseEntity<Void> leaveRoom(@AuthenticationPrincipal HotHatUser user) {
        clearMyActiveRoom.run(user);
        return ResponseEntity.noContent().build();
    }
}
