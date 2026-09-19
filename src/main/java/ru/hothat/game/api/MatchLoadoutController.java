package ru.hothat.game.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.ArsenalResponseDTO;
import ru.hothat.game.api.dto.MatchLoadoutResponseDTO;
import ru.hothat.game.api.dto.ReplaceLoadoutSlotRequestDTO;
import ru.hothat.game.api.dto.ReplacedLoadoutSlotResponseDTO;
import ru.hothat.game.api.dto.SetMatchLoadoutRequestDTO;
import ru.hothat.game.usecase.GetMyArsenalUseCase;
import ru.hothat.game.usecase.ReplaceLoadoutSlotUseCase;
import ru.hothat.game.usecase.SetMatchLoadoutUseCase;

/**
 * Боевое снаряжение игрока: боезапас и обойма мемов.
 *
 * <p>Только своё. Чужой боезапас — половина тактики, а раньше он приезжал
 * каждому вместе с подпиской на игроков комнаты.
 *
 * <p>Обойма целиком заряжается между партиями, слот меняется в партии — и это
 * разные адреса, потому что разрешены они в разное время.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/game/{roomId}/players/me")
@Tag(name = "Game · снаряжение", description = "Боезапас и обойма мемов")
@SecurityRequirement(name = "Bearer")
@PreAuthorize("@gameAuthz.isPlayer(#roomId)")
public class MatchLoadoutController {

    private final GetMyArsenalUseCase getArsenal;
    private final SetMatchLoadoutUseCase setLoadout;
    private final ReplaceLoadoutSlotUseCase replaceSlot;

    @Operation(summary = "Прочитать своё снаряжение",
            description = "Боезапас, три очереди мемов, перезарядка и свои клипы Подмены.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Снаряжение"),
            @ApiResponse(responseCode = "403", description = "PLAYER_NOT_FOUND: вы не игрок этой партии",
                    content = @Content)})
    @GetMapping("/arsenal")
    public ResponseEntity<ArsenalResponseDTO> arsenal(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId) {
        return ResponseEntity.ok(getArsenal.run(user, roomId));
    }

    @Operation(summary = "Зарядить обойму",
            description = "Пять разных существующих мемов. Только между партиями: в идущей партии "
                    + "очередь уже раздана, и подмена всей обоймы выдала бы новые заряды.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Обойма заряжена"),
            @ApiResponse(responseCode = "403", description = "PLAYER_NOT_FOUND: вы не игрок этой партии",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "MEME_NOT_LOADED: такого мема нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "LOADOUT_LOCKED, MEME_DUPLICATE",
                    content = @Content)})
    @PutMapping("/loadout")
    public ResponseEntity<MatchLoadoutResponseDTO> loadout(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId,
            @Valid @RequestBody SetMatchLoadoutRequestDTO request) {
        return ResponseEntity.ok(setLoadout.run(user, roomId, request));
    }

    @Operation(summary = "Заменить мем в слоте",
            description = "Можно в любой момент партии, кроме собственного хода: активная команда "
                    + "подбирала бы ролик под то, что происходит на сцене прямо сейчас.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Мем заменён либо уже стоял в этом слоте"),
            @ApiResponse(responseCode = "403", description = "PLAYER_NOT_FOUND: вы не игрок этой партии",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "MEME_NOT_LOADED", content = @Content),
            @ApiResponse(responseCode = "409", description = "ACTIVE_TEAM_LOADOUT_LOCKED, MEME_DUPLICATE, "
                    + "LOADOUT_REQUIRED", content = @Content)})
    @PutMapping("/loadout/slots/{slotIndex}")
    public ResponseEntity<ReplacedLoadoutSlotResponseDTO> replaceSlot(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId,
            @Parameter(description = "Номер слота обоймы, 0..4")
            @PathVariable @Min(value = 0, message = "Некорректный слот мема.")
            @Max(value = 4, message = "Некорректный слот мема.") int slotIndex,
            @Valid @RequestBody ReplaceLoadoutSlotRequestDTO request) {
        return ResponseEntity.ok(replaceSlot.run(user, roomId, slotIndex, request));
    }
}
