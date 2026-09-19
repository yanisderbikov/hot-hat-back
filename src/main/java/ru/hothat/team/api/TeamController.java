package ru.hothat.team.api;

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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.team.api.dto.FoundTeamRequestDTO;
import ru.hothat.team.api.dto.FoundedTeamResponseDTO;
import ru.hothat.team.api.dto.PublicTeamResponseDTO;
import ru.hothat.team.usecase.FoundTeamUseCase;
import ru.hothat.team.usecase.GetPublicTeamUseCase;

/**
 * Команда как ресурс: основание новой и чтение чужой публичной карточки.
 *
 * <p>Заменяет часть {@code POST /api/portal} с действиями {@code create_team} и
 * {@code public_team_profile}.
 *
 * <p>Две операции в одном классе, потому что у них один уровень прав — любой
 * вошедший игрок — и один ресурс: команда вообще, а не своя. Своя команда
 * живёт под {@code /api/v2/team/me} и предъявляет другие требования, поэтому
 * её адреса вынесены отдельно. Литеральный сегмент {@code me} при разборе пути
 * побеждает образец {@code {teamId}}, так что чтение своей команды сюда не
 * попадает.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/team")
@Tag(name = "Team · команда", description = "Основание команды и публичная карточка")
@SecurityRequirement(name = "Bearer")
public class TeamController {

    private final FoundTeamUseCase foundTeam;
    private final GetPublicTeamUseCase getPublicTeam;

    @Operation(summary = "Основать команду",
            description = "Создаёт команду и зовёт в неё напарника. Играть ею нельзя, пока напарник "
                    + "не примет приглашение. Напарник должен быть вашим другом и играть в том же "
                    + "дивизионе, а ни один из двоих — не состоять в другой команде.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Команда основана, приглашение ушло"),
            @ApiResponse(responseCode = "404", description = "PARTNER_NOT_FOUND: игрока с таким ником нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "ALREADY_IN_TEAM, PARTNER_ALREADY_IN_TEAM, "
                    + "TEAM_NAME_TAKEN, TEAM_DIVISION_MISMATCH, PARTNER_MUST_BE_FRIEND",
                    content = @Content)})
    @PostMapping
    public ResponseEntity<FoundedTeamResponseDTO> found(@AuthenticationPrincipal HotHatUser user,
                                                        @Valid @RequestBody FoundTeamRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(foundTeam.run(user, request));
    }

    @Operation(summary = "Показать чужую команду",
            description = "Публичная карточка: название, логотип, дивизион, оба участника и "
                    + "показатели текущего сезона. Открывается из таблицы рейтингов.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Карточка команды"),
            @ApiResponse(responseCode = "404", description = "TEAM_NOT_FOUND: команды нет или её распустили",
                    content = @Content)})
    @GetMapping("/{teamId}")
    public ResponseEntity<PublicTeamResponseDTO> publicProfile(
            /*
             * Форма взята по колонке (80 знаков), а не по генератору
             * («rt-» и шестнадцать шестнадцатеричных цифр): правило на входе
             * не должно отвергать команду, которую база хранит и признаёт.
             * Незнакомый идентификатор всё равно упрётся в 404 — это честнее,
             * чем 400 «некорректная команда» про существующую команду.
             */
            @Parameter(description = "Идентификатор команды", example = "0f3a9c1d-7b2e-4a58-9c40-6d5e2b8a1f37")
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,80}$", message = "Некорректная команда.")
            String teamId) {
        return ResponseEntity.ok(getPublicTeam.run(teamId));
    }
}
