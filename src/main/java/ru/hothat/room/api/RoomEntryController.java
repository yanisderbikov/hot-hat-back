package ru.hothat.room.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.room.api.dto.CreateRoomRequestDTO;
import ru.hothat.room.api.dto.CreatedRoomResponseDTO;
import ru.hothat.room.api.dto.EnterRoomRequestDTO;
import ru.hothat.room.api.dto.RoomSeatResponseDTO;
import ru.hothat.room.api.dto.SpectatorSeatResponseDTO;
import ru.hothat.room.usecase.CreateRoomUseCase;
import ru.hothat.room.usecase.EnterRoomUseCase;
import ru.hothat.room.usecase.TakeSpectatorSeatUseCase;

/**
 * Как игрок попадает в комнату.
 *
 * <p>Заменяет пакет записей, который до сих пор делал браузер: создание комнаты
 * ({@code app-core.js:12101}), вход игроком ({@code :12277}) и вход зрителем
 * ({@code :12373}). Все три писали документы напрямую, а путь {@code rooms/*}
 * в проверке прав на запись пуст (находка A1) — то есть хозяйство, имя и
 * счётчик игроков были полями, которые вызывающий назначал себе сам.
 *
 * <p>Три операции в одном классе, потому что у них один уровень прав: любой
 * вошедший игрок. Всё, что требует уже занятого места, живёт в соседних
 * классах — там предикаты строже.
 *
 * <p>{@code PUT}, а не {@code POST}: место в комнате — это ресурс с известным
 * адресом ({@code players/me}), и повторный вход в ту же комнату не должен
 * заводить второе место. Идемпотентность здесь не украшение: вкладка,
 * перезагруженная на плохой связи, повторяет вызов сама.
 *
 * <p>Гостю сюда нельзя, и это проверяется явно: гость смотрит превью с главной,
 * а место в комнате занимает только зарегистрированный.
 *
 * <p>Старые пути пока живы: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/room")
@PreAuthorize("hasRole('USER')")
@Tag(name = "Room · вход", description = "Создание комнаты и вход в неё")
@SecurityRequirement(name = "Bearer")
public class RoomEntryController {

    private final CreateRoomUseCase createRoom;
    private final EnterRoomUseCase enterRoom;
    private final TakeSpectatorSeatUseCase takeSpectatorSeat;

    @Operation(summary = "Создать комнату",
            description = "Заводит комнату и сажает создателя в неё хозяином — одной транзакцией. "
                    + "Идентификатор, хозяина, фазу и дивизион ставит сервер; дивизион берётся из "
                    + "карточки игрока, потому что комната в чужом дивизионе не впустила бы своего "
                    + "же создателя.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Комната создана"),
            @ApiResponse(responseCode = "409", description = "DEFAULT_LOADOUT_REQUIRED: не заряжены "
                    + "пять мемов", content = @Content)})
    @PostMapping
    public ResponseEntity<CreatedRoomResponseDTO> create(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody CreateRoomRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createRoom.run(user, request));
    }

    @Operation(summary = "Занять место игрока",
            description = "Заводит место или чинит существующее: повторный вход возвращает игрока "
                    + "на его место, а не создаёт второе. В идущую партию впускает только "
                    + "участников замороженного состава.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Место занято"),
            @ApiResponse(responseCode = "403", description = "RANKED_DIVISION_MISMATCH, "
                    + "ROOM_DIVISION_MISMATCH: комната играет в другом дивизионе; "
                    + "PRIVATE_GAME_STARTED: в приватную партию возвращают только по сохранённому "
                    + "месту", content = @Content),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: комнаты нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "ROOM_FULL: мест нет; "
                    + "GAME_ALREADY_STARTED: партия идёт без вас; ROOM_CLOSED: комната закрыта; "
                    + "DEFAULT_LOADOUT_REQUIRED: не заряжены пять мемов", content = @Content)})
    @PutMapping("/{roomId}/players/me")
    public ResponseEntity<RoomSeatResponseDTO> enter(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Valid @RequestBody(required = false) EnterRoomRequestDTO request) {
        return ResponseEntity.ok(enterRoom.run(user, roomId, request));
    }

    @Operation(summary = "Занять место зрителя",
            description = "Смотреть можно только чужую публичную комнату и только после начала "
                    + "партии: до старта все, кто внутри, — игроки. Тела у запроса нет: имя и "
                    + "аватар сервер берёт из карточки.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Место зрителя занято"),
            @ApiResponse(responseCode = "403", description = "PRIVATE_ROOM_NOT_WATCHABLE: комната "
                    + "приватная", content = @Content),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: комнаты нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "SPECTATORS_AFTER_START: партия ещё не "
                    + "началась; ROOM_CLOSED: комната закрыта; PLAYER_CANNOT_WATCH: у вас здесь "
                    + "место игрока — возвращайтесь за стол", content = @Content)})
    @PutMapping("/{roomId}/spectators/me")
    public ResponseEntity<SpectatorSeatResponseDTO> watch(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId) {
        return ResponseEntity.ok(takeSpectatorSeat.run(user, roomId));
    }
}
