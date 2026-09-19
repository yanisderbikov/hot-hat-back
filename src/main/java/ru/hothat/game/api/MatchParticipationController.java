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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.MatchPresenceResponseDTO;
import ru.hothat.game.api.dto.RankedAutostartResponseDTO;
import ru.hothat.game.api.dto.SubmitWordsRequestDTO;
import ru.hothat.game.api.dto.SubmittedWordsResponseDTO;
import ru.hothat.game.usecase.AutostartRankedMatchUseCase;
import ru.hothat.game.usecase.ReconcileMatchPresenceUseCase;
import ru.hothat.game.usecase.SubmitWordsUseCase;

/**
 * Участие игрока в партии: слова, автозапуск, присутствие.
 *
 * <p>Три операции с общим уровнем прав — участник комнаты. Хозяйских прав им
 * не нужно: слова сдаёт каждый за себя, рейтинговую партию запускает первый
 * готовый, а сверку присутствия заказывает тот клиент, который взял на себя
 * опрос.
 *
 * <p>Заменяет действия {@code ranked_autostart} и {@code sync_game_presence}
 * старого {@code POST /api/game} и клиентскую транзакцию {@code saveWords()}.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/game/{roomId}")
@Tag(name = "Game · участие", description = "Слова, автозапуск и присутствие в партии")
@SecurityRequirement(name = "Bearer")
@PreAuthorize("@gameAuthz.isMember(#roomId)")
public class MatchParticipationController {

    private final AutostartRankedMatchUseCase autostart;
    private final SubmitWordsUseCase submitWords;
    private final ReconcileMatchPresenceUseCase reconcilePresence;

    @Operation(summary = "Запустить рейтинговую партию",
            description = "Вызывают все игроки сразу, как только видят полный состав: хозяина в "
                    + "рейтинге нет. Партию начинает первый пришедший запрос, остальные получают "
                    + "ALREADY_RUNNING и то же состояние.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Партия начата, ждём остальных либо уже идёт"),
            @ApiResponse(responseCode = "409", description = "RANKED_ONLY, TEAMS_NOT_READY, LOADOUT_REQUIRED",
                    content = @Content)})
    @PostMapping("/ranked-autostart")
    public ResponseEntity<RankedAutostartResponseDTO> autostart(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId) {
        return ResponseEntity.ok(autostart.run(user, roomId));
    }

    @Operation(summary = "Сдать слова в шляпу",
            description = "Слова добавляются к уже сданным, повторы и пустые строки отбрасываются. "
                    + "Чужие слова не отдаются никому: в ответе своя пачка и общий счётчик.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Слова сохранены"),
            @ApiResponse(responseCode = "409", description = "WORDS_LOCKED: партия уже началась",
                    content = @Content)})
    @PutMapping("/word-submissions/me")
    public ResponseEntity<SubmittedWordsResponseDTO> submitWords(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId,
            @Valid @RequestBody SubmitWordsRequestDTO request) {
        return ResponseEntity.ok(submitWords.run(user, roomId, request));
    }

    @Operation(summary = "Отметиться и сверить присутствие",
            description = "Отмечает вызывающего и сверяет состав партии со списком тех, кто на "
                    + "видеосвязи: ставит паузу, снимает её и засчитывает техническое завершение, "
                    + "если пропавшего ждали слишком долго.")
    @ApiResponse(responseCode = "200", description = "Итог сверки")
    @PutMapping("/players/me/heartbeat")
    public ResponseEntity<MatchPresenceResponseDTO> heartbeat(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId) {
        return ResponseEntity.ok(reconcilePresence.run(user, roomId));
    }
}
