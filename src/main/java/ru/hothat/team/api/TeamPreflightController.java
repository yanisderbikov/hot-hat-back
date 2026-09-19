package ru.hothat.team.api;

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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.team.api.dto.PreflightSessionResponseDTO;
import ru.hothat.team.api.dto.PreflightStatusResponseDTO;
import ru.hothat.team.api.dto.StartPreflightRequestDTO;
import ru.hothat.team.usecase.CancelPreflightUseCase;
import ru.hothat.team.usecase.GetPreflightSessionUseCase;
import ru.hothat.team.usecase.StartPreflightUseCase;

/**
 * Проверка готовности пары перед рейтинговой игрой.
 *
 * <p>Заменяет {@code POST /api/portal} с действиями {@code team_preflight_start},
 * {@code team_preflight_status} и {@code team_preflight_cancel}.
 *
 * <p>Проверка — это ресурс со своим жизненным циклом, а не набор действий:
 * у пары она одна, она создаётся, читается, отменяется и протухает через пять
 * минут. Поэтому три глагола на одном адресе, а не три адреса с глаголами в
 * пути. Отчёты участников о себе — вложенный ресурс: у них другой предмет
 * (участник, а не проверка) и другая частота.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/team/me/preflight")
@Tag(name = "Team · готовность", description = "Проверка готовности пары перед рейтинговой игрой")
@SecurityRequirement(name = "Bearer")
public class TeamPreflightController {

    private final StartPreflightUseCase startPreflight;
    private final GetPreflightSessionUseCase getPreflight;
    private final CancelPreflightUseCase cancelPreflight;

    @Operation(summary = "Начать проверку",
            description = "Создаёт проверку на пять минут и полностью заменяет предыдущую: флаги "
                    + "связи и готовности не переносятся. Требует заряженной обоймы мемов у обоих, "
                    + "а в режиме диверсий — оставшегося лимита бесплатных партий у обоих.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Проверка создана"),
            @ApiResponse(responseCode = "402", description = "TEAMMATE_SABOTAGE_LIMIT_REACHED: у напарника "
                    + "кончились бесплатные партии с диверсиями", content = @Content),
            @ApiResponse(responseCode = "409", description = "RANKED_TEAM_REQUIRED, DEFAULT_LOADOUT_REQUIRED, "
                    + "RANKED_ROOM_UNAVAILABLE, RANKED_ROOM_MODE_MISMATCH, RANKED_ROOM_DIVISION_MISMATCH",
                    content = @Content)})
    @PostMapping
    public ResponseEntity<PreflightSessionResponseDTO> start(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody StartPreflightRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(startPreflight.run(user, request));
    }

    @Operation(summary = "Прочитать проверку",
            description = "Снимок с флагами обоих участников. Единственный способ увидеть связь и "
                    + "готовность напарника. Протухшую проверку сервер не отдаёт: поле session пусто.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Снимок проверки или пустое поле session"),
            @ApiResponse(responseCode = "409", description = "RANKED_TEAM_REQUIRED: подтверждённой "
                    + "команды нет", content = @Content)})
    @GetMapping
    public ResponseEntity<PreflightStatusResponseDTO> status(@AuthenticationPrincipal HotHatUser user) {
        return ResponseEntity.ok(getPreflight.run(user));
    }

    @Operation(summary = "Отменить проверку",
            description = "Повторная отмена — не ошибка: удалять нечего, состояние то же самое. "
                    + "Начатый поиск соперников этим не снимается, у билета подбора свой адрес.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Проверки больше нет"),
            @ApiResponse(responseCode = "409", description = "RANKED_TEAM_REQUIRED: подтверждённой "
                    + "команды нет", content = @Content)})
    @DeleteMapping
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal HotHatUser user) {
        cancelPreflight.run(user);
        return ResponseEntity.noContent().build();
    }
}
