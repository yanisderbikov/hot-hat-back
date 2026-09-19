package ru.hothat.team.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.team.api.dto.PreflightMediaResponseDTO;
import ru.hothat.team.api.dto.PreflightReadinessResponseDTO;
import ru.hothat.team.api.dto.ReportPreflightMediaRequestDTO;
import ru.hothat.team.api.dto.SetPreflightReadinessRequestDTO;
import ru.hothat.team.usecase.ReportPreflightMediaUseCase;
import ru.hothat.team.usecase.SetPreflightReadinessUseCase;

/**
 * Что участник сообщает о себе в идущей проверке готовности.
 *
 * <p>Заменяет {@code POST /api/portal} с действиями {@code team_preflight_media}
 * и {@code team_preflight_ready}.
 *
 * <p>Класс отделён от самой проверки не по предмету, а по уровню прав: там пара
 * распоряжается общей проверкой, здесь участник — только своей строкой в ней.
 * Отсюда и {@code participants/me} в пути: чужую готовность поставить нельзя,
 * и адреса для этого не существует.
 *
 * <p>Оба глагола — {@code PUT}: это не события, а два значения, которые
 * участник переставляет туда и обратно. Повторная отправка того же значения
 * ничего не меняет.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/team/me/preflight/participants/me")
@Tag(name = "Team · участник проверки", description = "Связь и готовность одного участника")
@SecurityRequirement(name = "Bearer")
public class PreflightParticipantController {

    private final ReportPreflightMediaUseCase reportMedia;
    private final SetPreflightReadinessUseCase setReadiness;

    @Operation(summary = "Отчитаться о камере и микрофоне",
            description = "Флаг протухает за 25 секунд: ответ называет и серверное время приёма, и "
                    + "срок жизни, чтобы клиент не держал их своими константами. Потерянная связь "
                    + "заодно снимает готовность — играть вслепую нельзя.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отчёт записан"),
            @ApiResponse(responseCode = "409", description = "PREFLIGHT_REQUIRED: проверки нет или она "
                    + "протухла; RANKED_TEAM_REQUIRED: подтверждённой команды нет", content = @Content)})
    @PutMapping("/media-check")
    public ResponseEntity<PreflightMediaResponseDTO> media(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody ReportPreflightMediaRequestDTO request) {
        return ResponseEntity.ok(reportMedia.run(user, request));
    }

    @Operation(summary = "Поставить или снять готовность",
            description = "Готовность требует подтверждённой связи. В ответ едет снимок проверки "
                    + "целиком: нажатие меняет и сводные признаки, по которым включается запуск.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Готовность записана"),
            @ApiResponse(responseCode = "409", description = "MEDIA_NOT_READY: связь не подтверждена; "
                    + "PREFLIGHT_REQUIRED: проверки нет или она протухла", content = @Content)})
    @PutMapping("/readiness")
    public ResponseEntity<PreflightReadinessResponseDTO> readiness(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody SetPreflightReadinessRequestDTO request) {
        return ResponseEntity.ok(setReadiness.run(user, request));
    }
}
