package ru.hothat.rating.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.rating.api.dto.SubmitMatchResultRequestDTO;
import ru.hothat.rating.api.dto.SubmittedMatchResultResponseDTO;
import ru.hothat.rating.usecase.RecordMatchResultUseCase;

/**
 * Зачёт результата рейтинговой партии.
 *
 * <p>Заменяет {@code POST /api/portal} с {@code action=record_result}. Зовёт
 * его клиент из обработчика конца партии ({@code app-core.js:8709}), то есть
 * каждый участник и по разу за партию — поэтому зачёт идемпотентен по паре
 * «комната + номер партии», а повторный вызов не ошибка, а исход
 * {@code already_recorded}.
 *
 * <p>Отдельный класс от таблиц, потому что уровень прав другой: таблицы читает
 * любой вошедший, а очки начисляет только участник этой рейтинговой партии.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на этот маршрут отдельно.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/rating/match-results")
@Tag(name = "Rating · зачёт партии", description = "Запись результата рейтинговой партии")
@SecurityRequirement(name = "Bearer")
public class MatchResultSubmissionController {

    private final RecordMatchResultUseCase recordMatchResult;

    @Operation(summary = "Зачесть результат партии",
            description = "Начисляет очки командам за занятые места, наказывает за обрыв связи и "
                    + "обновляет личные строки. Ответ один на все исходы: что именно произошло, "
                    + "сказано полем outcome, а не набором ключей. Повтор по уже зачтённой партии "
                    + "тоже отвечает 201 — состояние сервера после него такое же, как после первого "
                    + "вызова, и клиенту незачем различать это ещё и по коду.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Результат зачтён либо уже был зачтён раньше"),
            @ApiResponse(responseCode = "403", description = "PLAYER_NOT_FOUND: вы не играли в этой партии",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: такой комнаты нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "NOT_RANKED_RESULT: партия не рейтинговая "
                    + "или ещё не закончилась; RANKED_TEAMS_MISSING: в комнате меньше двух "
                    + "рейтинговых команд", content = @Content)})
    @PostMapping
    public ResponseEntity<SubmittedMatchResultResponseDTO> submit(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody SubmitMatchResultRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recordMatchResult.run(user, request));
    }
}
