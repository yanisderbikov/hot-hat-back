package ru.hothat.game.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.AdvanceTurnRequestDTO;
import ru.hothat.game.api.dto.ExpireTurnRequestDTO;
import ru.hothat.game.api.dto.NextTurnResponseDTO;
import ru.hothat.game.api.dto.TurnExpiredResponseDTO;
import ru.hothat.game.usecase.AdvanceTurnUseCase;
import ru.hothat.game.usecase.ExpireTurnUseCase;

/**
 * Продвижение партии по серверным часам.
 *
 * <p>Обе операции доступны любому участнику, а не только объясняющему, и это
 * не послабление, а страховка: у объясняющего может закрыться браузер ровно
 * на последней секунде, и без чужого запроса партия зависла бы навсегда.
 * Поторопить ход этим нельзя — время считает сервер.
 *
 * <p>Заменяет клиентские процедуры {@code ensureExhaustedTurnAdvanced()} и
 * транзакцию {@code nextTurn()}.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/game/{roomId}/turn")
@Tag(name = "Game · часы хода", description = "Истечение хода и передача очереди")
@SecurityRequirement(name = "Bearer")
@PreAuthorize("@gameAuthz.isMember(#roomId)")
public class TurnClockController {

    private final ExpireTurnUseCase expireTurn;
    private final AdvanceTurnUseCase advanceTurn;

    @Operation(summary = "Закрыть истёкший ход",
            description = "До дедлайна отвечает NOT_YET и остатком времени. Тот же адрес вытаскивает "
                    + "партию из состояния «шляпа пуста, а ход открыт».")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ход закрыт, был закрыт раньше либо ещё идёт"),
            @ApiResponse(responseCode = "409", description = "TURN_STALE: речь о ходе, которого уже нет",
                    content = @Content)})
    @PostMapping("/expiry")
    public ResponseEntity<TurnExpiredResponseDTO> expire(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId,
            @Valid @RequestBody ExpireTurnRequestDTO request) {
        return ResponseEntity.ok(expireTurn.run(user, roomId, request));
    }

    @Operation(summary = "Передать очередь",
            description = "Следующая команда по порядку; порядок считает сервер. Идемпотентно по "
                    + "идентификатору закончившегося хода: два нажатия не перескочат через команду.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Очередь передана либо была передана раньше"),
            @ApiResponse(responseCode = "409", description = "TURN_STALE, GAME_PAUSED", content = @Content)})
    @PostMapping("/next")
    public ResponseEntity<NextTurnResponseDTO> next(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId,
            @Valid @RequestBody AdvanceTurnRequestDTO request) {
        return ResponseEntity.ok(advanceTurn.run(user, roomId, request));
    }
}
