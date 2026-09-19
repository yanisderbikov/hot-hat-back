package ru.hothat.game.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.MatchStateResponseDTO;
import ru.hothat.game.usecase.GetMatchStateUseCase;

/**
 * Состояние партии.
 *
 * <p>Один композитный ответ вместо чтения документа комнаты и трёх подписок.
 * Слово текущего хода видит только объясняющий — всем остальным, включая
 * зрителей и того, кто угадывает, поле приходит пустым.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/game/{roomId}")
@Tag(name = "Game · состояние", description = "Снимок текущей партии")
@SecurityRequirement(name = "Bearer")
@PreAuthorize("@gameAuthz.isMemberOrSpectator(#roomId)")
public class MatchStateController {

    private final GetMatchStateUseCase getMatchState;

    @Operation(summary = "Прочитать партию",
            description = "Фаза, счёт, ход, пауза, голосование и своё снаряжение одним ответом. "
                    + "Вместе с ними приезжает серверное время: все сроки партии заданы в нём.")
    @ApiResponse(responseCode = "200", description = "Снимок партии")
    @GetMapping
    public ResponseEntity<MatchStateResponseDTO> state(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId) {
        return ResponseEntity.ok(getMatchState.run(user, roomId));
    }
}
