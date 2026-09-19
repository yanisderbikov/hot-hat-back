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
import ru.hothat.game.api.dto.AppealResultResponseDTO;
import ru.hothat.game.api.dto.AppealVotesResponseDTO;
import ru.hothat.game.api.dto.CastAppealVoteRequestDTO;
import ru.hothat.game.api.dto.CloseAppealRequestDTO;
import ru.hothat.game.usecase.CastAppealVoteUseCase;
import ru.hothat.game.usecase.CloseAppealUseCase;

/**
 * Голосование по спорным словам закончившегося хода.
 *
 * <p>Заменяет действия {@code appeal_vote} и {@code finalize_appeal} старого
 * {@code POST /api/game}.
 *
 * <p>Голос — это PUT со значением, а не переключатель: раньше клиент вычислял
 * новое значение от своего снимка и на разъехавшемся снимке отправлял то, что
 * уже стоит.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/game/{roomId}/appeal")
@Tag(name = "Game · апелляция", description = "Голосование по спорным словам")
@SecurityRequirement(name = "Bearer")
@PreAuthorize("@gameAuthz.isPlayer(#roomId)")
public class AppealController {

    private final CastAppealVoteUseCase castVote;
    private final CloseAppealUseCase closeAppeal;

    @Operation(summary = "Проголосовать за отмену слова",
            description = "Голосуют все игроки партии, кроме разбираемой команды. Слово отменяется, "
                    + "когда за это большинство имеющих право голоса.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Голос учтён"),
            @ApiResponse(responseCode = "403", description = "APPEAL_NOT_ELIGIBLE: своя команда не голосует",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "WORD_NOT_IN_TURN: этого слова в ходе не было",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "APPEAL_NOT_ACTIVE, APPEAL_CLOSED, GAME_PAUSED",
                    content = @Content)})
    @PutMapping("/votes/{wordId}")
    public ResponseEntity<AppealVotesResponseDTO> vote(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId,
            @Parameter(description = "Слово хода, о котором идёт спор")
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{6,80}$", message = "Некорректное слово хода.")
            String wordId,
            @Valid @RequestBody CastAppealVoteRequestDTO request) {
        return ResponseEntity.ok(castVote.run(user, roomId, wordId, request));
    }

    @Operation(summary = "Подвести итог голосования",
            description = "Отменяет слова, возвращает их в шляпу, начисляет команде награды и передаёт "
                    + "очередь — одной транзакцией. Идемпотентно по идентификатору хода: просьбу шлют "
                    + "все сразу, а начисление должно случиться один раз.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Итоги подведены либо были подведены раньше"),
            @ApiResponse(responseCode = "409", description = "APPEAL_NOT_ACTIVE, TURN_STALE, GAME_PAUSED",
                    content = @Content)})
    @PostMapping("/closing")
    public ResponseEntity<AppealResultResponseDTO> close(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId,
            @Valid @RequestBody CloseAppealRequestDTO request) {
        return ResponseEntity.ok(closeAppeal.run(user, roomId, request));
    }
}
