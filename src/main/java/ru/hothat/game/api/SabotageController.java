package ru.hothat.game.api;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.OrderReplacementClipRequestDTO;
import ru.hothat.game.api.dto.ReplacementClipResponseDTO;
import ru.hothat.game.api.dto.ReportClipResultRequestDTO;
import ru.hothat.game.api.dto.ReportedClipResultResponseDTO;
import ru.hothat.game.api.dto.SabotageEventResponseDTO;
import ru.hothat.game.api.dto.UseWeaponRequestDTO;
import ru.hothat.game.usecase.DiscardReplacementClipUseCase;
import ru.hothat.game.usecase.OrderReplacementClipUseCase;
import ru.hothat.game.usecase.ReportClipResultUseCase;
import ru.hothat.game.usecase.UseWeaponUseCase;

/**
 * Диверсии против объясняющего.
 *
 * <p>Один адрес на все тринадцать видов оружия, а не тринадцать адресов по
 * видам, — решение заказчика (вопрос 2 плана). Вид оружия приезжает
 * перечислением, поэтому неизвестное значение отвергает Jackson до входа сюда,
 * а ветвление по виду делает реестр обработчиков в домене. В контроллере
 * {@code switch} по полю тела нет и быть не может.
 *
 * <p>Клипы Подмены живут отдельным ресурсом: их снимают заранее, они
 * принадлежат снявшему, и итог съёмки присылает тот, кого снимали.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/game/{roomId}")
@Tag(name = "Game · диверсии", description = "Оружие и клипы Подмены")
@SecurityRequirement(name = "Bearer")
@PreAuthorize("@gameAuthz.isPlayer(#roomId)")
public class SabotageController {

    private final UseWeaponUseCase useWeapon;
    private final OrderReplacementClipUseCase orderClip;
    private final ReportClipResultUseCase reportClipResult;
    private final DiscardReplacementClipUseCase discardClip;

    @Operation(summary = "Применить оружие",
            description = "Проверяет фазу, режим, принадлежность к партии, перезарядку, занятость "
                    + "дорожки эффекта, остаток хода и боезапас, затем списывает заряд и рассылает "
                    + "событие сцене.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Диверсия применена"),
            @ApiResponse(responseCode = "403", description = "ACTIVE_TEAM_CANNOT_ATTACK, "
                    + "WEAPON_NOT_ELIGIBLE, OWNER_ONLY, MEME_NOT_LOADED", content = @Content),
            @ApiResponse(responseCode = "409", description = "ROUND_NOT_ACTIVE, GAME_PAUSED, NO_AMMO, "
                    + "VIDEO_EFFECT_BUSY, VOICE_EFFECT_BUSY, OVERLAY_EFFECT_BUSY, REPLACEMENT_ACTIVE, "
                    + "NOT_ENOUGH_TURN_TIME, MEME_IN_RESERVE", content = @Content),
            @ApiResponse(responseCode = "429", description = "WEAPON_COOLDOWN: оружие перезаряжается",
                    content = @Content)})
    @PostMapping("/sabotages")
    public ResponseEntity<SabotageEventResponseDTO> use(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId,
            @Valid @RequestBody UseWeaponRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useWeapon.run(user, roomId, request));
    }

    @Operation(summary = "Заказать съёмку клипа Подмены",
            description = "Снимает объясняющего десять секунд. Слотов три, заряд при съёмке не "
                    + "тратится, а до конца хода должно оставаться не меньше одиннадцати секунд.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Съёмка начата"),
            @ApiResponse(responseCode = "403", description = "PLAYER_NOT_FOUND: вы не игрок этой партии",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "REPLACEMENT_RECORD_LIMIT, "
                    + "REPLACEMENT_RECORD_BUSY, REPLACEMENT_WRONG_TARGET, NOT_ENOUGH_TURN_TIME, "
                    + "ROUND_NOT_ACTIVE, WEAPON_INVALID", content = @Content)})
    @PostMapping("/replacement-clips")
    public ResponseEntity<ReplacementClipResponseDTO> orderClip(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId,
            @Valid @RequestBody OrderReplacementClipRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderClip.run(user, roomId, request));
    }

    @Operation(summary = "Сообщить итог съёмки",
            description = "Присылает тот, кого снимали: плёнка писалась в его браузере. Неудачная "
                    + "съёмка стирается сразу и освобождает слот заказчика.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Итог принят"),
            @ApiResponse(responseCode = "403", description = "PLAYER_NOT_FOUND: вы не игрок этой партии",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "REPLACEMENT_CLIP_INVALID: клип чужой "
                    + "или его больше нет", content = @Content)})
    @PutMapping("/replacement-clips/{clipId}/result")
    public ResponseEntity<ReportedClipResultResponseDTO> clipResult(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId,
            @Parameter(description = "Клип, о котором докладывают")
            @PathVariable @Pattern(regexp = "^repl_[a-f0-9]{20}$", message = "Некорректная запись Подмены.")
            String clipId,
            @Valid @RequestBody ReportClipResultRequestDTO request) {
        return ResponseEntity.ok(reportClipResult.run(user, roomId, clipId, request));
    }

    @Operation(summary = "Отказаться от своего клипа",
            description = "Освобождает слот. Чужой клип стереть нельзя: авторство проверяет сценарий.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Клипа больше нет"),
            @ApiResponse(responseCode = "403", description = "PLAYER_NOT_FOUND: вы не игрок этой партии",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "REPLACEMENT_CLIP_INVALID", content = @Content)})
    @DeleteMapping("/replacement-clips/{clipId}")
    public ResponseEntity<Void> discardClip(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId,
            @Parameter(description = "Свой клип")
            @PathVariable @Pattern(regexp = "^repl_[a-f0-9]{20}$", message = "Некорректная запись Подмены.")
            String clipId) {
        discardClip.run(user, roomId, clipId);
        return ResponseEntity.noContent().build();
    }
}
