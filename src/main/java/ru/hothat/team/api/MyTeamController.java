package ru.hothat.team.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.team.api.dto.MyTeamResponseDTO;
import ru.hothat.team.api.dto.TeamLobbyResponseDTO;
import ru.hothat.team.usecase.EnsureTeamLobbyUseCase;
import ru.hothat.team.usecase.GetMyTeamUseCase;

/**
 * Своя команда: состав и её комната-лобби.
 *
 * <p>Заменяет часть {@code POST /api/portal} с действиями {@code my_team} и
 * {@code ensure_team_lobby}. Из старого ответа {@code my_team} здесь остались
 * только состав, напарник и показатели: приглашения уехали на
 * {@code /api/v2/team/invites}, снимок префлайта — на
 * {@code /api/v2/team/me/preflight}. Так и должно быть — состав не меняется
 * неделями, а флаги готовности опрашиваются раз в полторы секунды, и возить
 * их с одной частотой значит гонять логотипы ради двух булевых значений.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/team/me")
@Tag(name = "Team · своя команда", description = "Состав своей команды и её лобби")
@SecurityRequirement(name = "Bearer")
public class MyTeamController {

    private final GetMyTeamUseCase getMyTeam;
    private final EnsureTeamLobbyUseCase ensureTeamLobby;

    @Operation(summary = "Показать свою команду",
            description = "Состав, напарник и показатели сезона. Команды может не быть — тогда поля "
                    + "team, partner и stats пусты, а дивизион и его подпись приходят всё равно: "
                    + "форма основания показывает их как ограничение на выбор напарника.")
    @GetMapping
    public ResponseEntity<MyTeamResponseDTO> myTeam(@AuthenticationPrincipal HotHatUser user) {
        return ResponseEntity.ok(getMyTeam.run(user));
    }

    @Operation(summary = "Открыть лобби команды",
            description = "Приватная комната на двоих с постоянным адресом: точка сбора пары перед "
                    + "рейтинговой игрой. Вызов идемпотентен — повторный чинит существующую комнату "
                    + "и возвращает тот же адрес, а не заводит вторую.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Комната готова, пара рассажена"),
            @ApiResponse(responseCode = "409", description = "RANKED_TEAM_REQUIRED: команды нет или её "
                    + "не подтвердил напарник; TEAM_DIVISION_MISMATCH: вы сменили дивизион",
                    content = @Content)})
    @PostMapping("/lobby")
    public ResponseEntity<TeamLobbyResponseDTO> lobby(@AuthenticationPrincipal HotHatUser user) {
        return ResponseEntity.ok(ensureTeamLobby.run(user));
    }
}
