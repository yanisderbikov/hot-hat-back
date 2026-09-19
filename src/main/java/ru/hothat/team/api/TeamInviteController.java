package ru.hothat.team.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.team.api.dto.TeamInvitesResponseDTO;
import ru.hothat.team.usecase.ListMyTeamInvitesUseCase;

/**
 * Приглашения в команды, пришедшие текущему игроку.
 *
 * <p>Заменяет массив {@code invites} из {@code POST /api/portal} с действием
 * {@code my_team}. Отдельный адрес, потому что список приходит тому, у кого
 * команды ещё нет: спрашивать его ответом «моя команда» значило поднимать
 * состав, статистику сезона и снимок префлайта ради десяти строк.
 *
 * <p>Класс отделён от ответа на приглашение не по предмету, а по уровню прав:
 * здесь игрок читает свой список, там распоряжается конкретным приглашением,
 * и право на него принадлежит адресату.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/team/invites")
@Tag(name = "Team · приглашения", description = "Входящие приглашения в рейтинговые команды")
@SecurityRequirement(name = "Bearer")
public class TeamInviteController {

    private final ListMyTeamInvitesUseCase listMyInvites;

    @Operation(summary = "Показать приглашения",
            description = "Приглашения, ждущие ответа: команда, кто зовёт и в каком дивизионе. "
                    + "Отвеченные и отозванные сюда не попадают.")
    @GetMapping
    public ResponseEntity<TeamInvitesResponseDTO> list(@AuthenticationPrincipal HotHatUser user) {
        return ResponseEntity.ok(listMyInvites.run(user));
    }
}
