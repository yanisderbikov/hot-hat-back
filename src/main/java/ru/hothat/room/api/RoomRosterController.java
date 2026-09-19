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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.PlayerUid;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.room.api.dto.PromotedSpectatorResponseDTO;
import ru.hothat.room.api.dto.SendRoomInviteRequestDTO;
import ru.hothat.room.api.dto.SentRoomInviteResponseDTO;
import ru.hothat.room.usecase.EjectPlayerUseCase;
import ru.hothat.room.usecase.PromoteSpectatorUseCase;
import ru.hothat.room.usecase.SendRoomInviteUseCase;

/**
 * Кого хозяин добавляет в комнату и кого из неё убирает.
 *
 * <p>Заменяет {@code kick_player} ({@code app-core.js:9024}),
 * {@code promote_room_spectator} ({@code :4837}) и {@code send_room_invite}
 * ({@code :4798}).
 *
 * <p>Отдельный класс от прочих хозяйских распоряжений, потому что предмет
 * другой: там комната и её настройки, здесь — люди в ней. Уровень прав тот же,
 * хозяйский, и это не совпадение: решать, кто сидит за столом, должен один
 * человек.
 *
 * <p>Приглашение живёт здесь же, а не в переписке, хотя уезжает сообщением в
 * чат: зовут в комнату, и право на это — право хозяина комнаты. Строку
 * переписки и счётчик непрочитанного пишет владелец тех таблиц, в той же
 * транзакции (§7.3, строка 7).
 *
 * <p>Старые пути пока живы: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/room/{roomId}")
@PreAuthorize("hasRole('USER')")
@Tag(name = "Room · состав комнаты", description = "Приглашения, повышение зрителей, удаление")
@SecurityRequirement(name = "Bearer")
public class RoomRosterController {

    private final EjectPlayerUseCase ejectPlayer;
    private final PromoteSpectatorUseCase promoteSpectator;
    private final SendRoomInviteUseCase sendRoomInvite;

    @Operation(summary = "Удалить участника",
            description = "Только до начала партии: выгнать человека из идущей игры значит "
                    + "оставить его команду без пары. Удаление уже ушедшего ничего не меняет — "
                    + "хозяин мог нажать по устаревшему списку.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Участник удалён"),
            @ApiResponse(responseCode = "403", description = "HOST_ONLY: вы не хозяин комнаты",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "KICK_SELF_FORBIDDEN: себя этой кнопкой "
                    + "не удаляют; GAME_ALREADY_STARTED: партия уже идёт", content = @Content)})
    @DeleteMapping("/players/{uid}")
    public ResponseEntity<Void> eject(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Кого удалить", example = "2c9d4e7aP3lM5nB8vC1xZ0qW6eR4")
            @PathVariable @PlayerUid String uid) {
        ejectPlayer.run(user, roomId, uid);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Посадить зрителя за стол",
            description = "Только до начала партии и только если есть свободное место. Пять "
                    + "заряженных мемов у зрителя обязательны: отказ здесь дешевле, чем на старте, "
                    + "где в него упрётся вся комната.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Зритель стал игроком"),
            @ApiResponse(responseCode = "403", description = "HOST_ONLY: вы не хозяин комнаты",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "SPECTATOR_NOT_FOUND: такого зрителя "
                    + "нет; PLAYER_NOT_FOUND: у него нет карточки", content = @Content),
            @ApiResponse(responseCode = "409", description = "GAME_ALREADY_STARTED: партия уже идёт; "
                    + "ROOM_FULL: мест нет; DEFAULT_LOADOUT_REQUIRED: у зрителя не заряжены пять "
                    + "мемов", content = @Content)})
    @PostMapping("/spectators/{uid}/promotion")
    public ResponseEntity<PromotedSpectatorResponseDTO> promote(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Кого посадить за стол", example = "2c9d4e7aP3lM5nB8vC1xZ0qW6eR4")
            @PathVariable @PlayerUid String uid) {
        return ResponseEntity.ok(promoteSpectator.run(user, roomId, uid));
    }

    @Operation(summary = "Позвать друга в комнату",
            description = "Приглашение уезжает карточкой в личную переписку и живёт сутки. Ветка "
                    + "«он уже внутри» — поле ответа, а не вторая его форма. В рейтинговую комнату "
                    + "звать нельзя: её состав собирает подбор.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Приглашение отправлено либо друг уже "
                    + "в комнате"),
            // Код именно ROOM_HOST_ONLY: хозяйство здесь проверяет старый
            // движок приглашений (assertRoomHost), а не общая проверка комнаты,
            // отвечающая HOST_ONLY. Обещать клиенту второй код значило бы
            // послать его сравнивать со строкой, которой этот адрес не вернёт.
            @ApiResponse(responseCode = "403", description = "ROOM_HOST_ONLY: вы не хозяин комнаты; "
                    + "FRIEND_REQUIRED: звать можно только друга", content = @Content),
            @ApiResponse(responseCode = "409", description = "ROOM_INVITE_UNAVAILABLE: комната "
                    + "рейтинговая; ROOM_SETUP_ONLY: партия уже идёт; ROOM_CLOSED: комната закрыта",
                    content = @Content)})
    @PostMapping("/invites")
    public ResponseEntity<SentRoomInviteResponseDTO> invite(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Valid @RequestBody SendRoomInviteRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sendRoomInvite.run(user, roomId, request));
    }
}
