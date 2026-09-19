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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.CompleteTurnRequestDTO;
import ru.hothat.game.api.dto.GuessWordRequestDTO;
import ru.hothat.game.api.dto.GuessedWordResponseDTO;
import ru.hothat.game.api.dto.SkipWordRequestDTO;
import ru.hothat.game.api.dto.SkippedWordResponseDTO;
import ru.hothat.game.api.dto.TurnCompletedResponseDTO;
import ru.hothat.game.api.dto.TurnStartedResponseDTO;
import ru.hothat.game.usecase.BeginTurnUseCase;
import ru.hothat.game.usecase.CompleteTurnUseCase;
import ru.hothat.game.usecase.GuessWordUseCase;
import ru.hothat.game.usecase.SkipWordUseCase;

/**
 * Ход объясняющего.
 *
 * <p>Заменяет клиентские транзакции {@code beginTurn()}, {@code guessed()},
 * {@code skipWord()} и {@code endTurn()} — то есть весь движок хода, который
 * до сих пор исполнялся в браузере того, кто играет: он тянул слово из шляпы,
 * решал, истёк ли ход, и начислял очки своей команде.
 *
 * <p>Идентификатор слова стоит в адресе и придумывается клиентом. Это ключ
 * идемпотентности: повтор запроса при потере связи находит слово уже
 * разобранным и второго очка не даёт (риск 2 плана).
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/game/{roomId}/turn")
@Tag(name = "Game · ход", description = "Ход объясняющего: слова и завершение")
@SecurityRequirement(name = "Bearer")
@PreAuthorize("@gameAuthz.isExplainer(#roomId)")
public class TurnController {

    private final BeginTurnUseCase beginTurn;
    private final GuessWordUseCase guessWord;
    private final SkipWordUseCase skipWord;
    private final CompleteTurnUseCase completeTurn;

    @Operation(summary = "Начать ход",
            description = "Слово из шляпы тянет сервер, часы идут от этой секунды. Повтор при потере "
                    + "связи возвращает тот же ход и то же слово, второго не тянет.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ход начат либо уже идёт"),
            @ApiResponse(responseCode = "403", description = "TURN_NOT_YOURS: ход не вашей команды",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "ROUND_NOT_ACTIVE, GAME_PAUSED, "
                    + "TURN_ALREADY_STARTED, TEAMS_NOT_READY", content = @Content)})
    @PostMapping
    public ResponseEntity<TurnStartedResponseDTO> begin(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(beginTurn.run(user, roomId));
    }

    @Operation(summary = "Засчитать слово",
            description = "Очко команде и следующее слово в ответе. Идентификатор слова придумывает "
                    + "клиент: повтор с тем же идентификатором не начисляет второго очка.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Слово засчитано либо повтор распознан"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED: идентификатор нажатия "
                    + "не 6-80 знаков латиницы, цифр, дефиса и подчёркивания", content = @Content),
            @ApiResponse(responseCode = "403", description = "TURN_NOT_YOURS: засчитывает не объясняющий",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "TURN_STALE: ход уже сменился; "
                    + "ROUND_NOT_ACTIVE, GAME_PAUSED", content = @Content)})
    @PostMapping("/words/{wordId}/guess")
    public ResponseEntity<GuessedWordResponseDTO> guess(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId,
            @Parameter(description = "Идентификатор нажатия: по нему узнаётся повтор")
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{6,80}$", message = "Некорректное слово хода.")
            String wordId,
            @Valid @RequestBody GuessWordRequestDTO request) {
        return ResponseEntity.ok(guessWord.run(user, roomId, wordId, request));
    }

    @Operation(summary = "Пропустить слово",
            description = "Слово возвращается в шляпу и может выпасть снова. Идемпотентно так же, "
                    + "как засчитывание: иначе повтор прокрутил бы шляпу ещё на одно слово.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Слово пропущено либо повтор распознан"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED: идентификатор нажатия "
                    + "не 6-80 знаков латиницы, цифр, дефиса и подчёркивания", content = @Content),
            @ApiResponse(responseCode = "403", description = "TURN_NOT_YOURS: пропускает не объясняющий",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "TURN_STALE, ROUND_NOT_ACTIVE, GAME_PAUSED",
                    content = @Content)})
    @PostMapping("/words/{wordId}/skip")
    public ResponseEntity<SkippedWordResponseDTO> skip(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId,
            @Parameter(description = "Идентификатор нажатия: по нему узнаётся повтор")
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{6,80}$", message = "Некорректное слово хода.")
            String wordId,
            @Valid @RequestBody SkipWordRequestDTO request) {
        return ResponseEntity.ok(skipWord.run(user, roomId, wordId, request));
    }

    @Operation(summary = "Завершить ход",
            description = "Закрывает свой ход досрочно и объявляет голосование. Идемпотентно по "
                    + "идентификатору хода: двойное нажатие не закроет заодно и чужой ход.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ход закрыт либо был закрыт раньше"),
            @ApiResponse(responseCode = "403", description = "TURN_NOT_YOURS: закрывает не объясняющий",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "TURN_STALE, ROUND_NOT_ACTIVE, GAME_PAUSED",
                    content = @Content)})
    @PostMapping("/completion")
    public ResponseEntity<TurnCompletedResponseDTO> complete(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId,
            @Valid @RequestBody CompleteTurnRequestDTO request) {
        return ResponseEntity.ok(completeTurn.run(user, roomId, request));
    }
}
