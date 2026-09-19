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
import ru.hothat.game.api.dto.MatchPausedResponseDTO;
import ru.hothat.game.api.dto.MatchResumedResponseDTO;
import ru.hothat.game.api.dto.StartMatchRequestDTO;
import ru.hothat.game.api.dto.StartedMatchResponseDTO;
import ru.hothat.game.usecase.PauseMatchUseCase;
import ru.hothat.game.usecase.ResumeMatchUseCase;
import ru.hothat.game.usecase.StartMatchUseCase;

/**
 * Течение партии от имени хозяина комнаты.
 *
 * <p>Заменяет действия {@code prepare_game} и {@code toggle_pause} старого
 * {@code POST /api/game}, а вместе с ними — клиентскую транзакцию
 * {@code collectWordsAndStart}, собиравшую шляпу прямо в браузере.
 *
 * <p>Пауза — ресурс, а не переключатель: PUT её ставит, DELETE снимает.
 * Раньше на её месте был флаг в теле запроса, и клиент вычислял новое
 * значение сам, от своего снимка.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/game/{roomId}")
@Tag(name = "Game · партия", description = "Течение партии: старт и пауза")
@SecurityRequirement(name = "Bearer")
@PreAuthorize("@gameAuthz.isHost(#roomId)")
public class MatchLifecycleController {

    private final StartMatchUseCase startMatch;
    private final PauseMatchUseCase pauseMatch;
    private final ResumeMatchUseCase resumeMatch;

    @Operation(summary = "Начать партию",
            description = "Собирает шляпу из сданных слов, замораживает составы и имена, выдаёт всем "
                    + "стартовый боезапас. Требует от двух до пяти команд ровно по двое, заряженной "
                    + "обоймы у каждого и минимум пяти слов в шляпе.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Партия началась"),
            @ApiResponse(responseCode = "409", description = "GAME_ALREADY_STARTED, TEAMS_NOT_READY, "
                    + "LOADOUT_REQUIRED, NOT_ENOUGH_WORDS", content = @Content)})
    @PostMapping
    public ResponseEntity<StartedMatchResponseDTO> start(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId,
            @Valid @RequestBody StartMatchRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(startMatch.run(user, roomId, request));
    }

    @Operation(summary = "Поставить партию на паузу",
            description = "Остаток хода замораживается числом и вернётся при снятии паузы. Повторная "
                    + "постановка ничего не меняет и остаток не обнуляет.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Партия остановлена"),
            @ApiResponse(responseCode = "409", description = "ROUND_NOT_ACTIVE: партия не идёт",
                    content = @Content)})
    @PutMapping("/pause")
    public ResponseEntity<MatchPausedResponseDTO> pause(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId) {
        return ResponseEntity.ok(pauseMatch.run(user, roomId));
    }

    @Operation(summary = "Снять паузу",
            description = "Пока кто-то не вернулся на связь, партия остаётся на паузе и только меняет "
                    + "причину: доигрывать ход командой из одного человека нельзя.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Пауза снята либо оставлена с другой причиной"),
            @ApiResponse(responseCode = "409", description = "ROUND_NOT_ACTIVE: партия не идёт",
                    content = @Content)})
    @DeleteMapping("/pause")
    public ResponseEntity<MatchResumedResponseDTO> resume(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId) {
        return ResponseEntity.ok(resumeMatch.run(user, roomId));
    }
}
