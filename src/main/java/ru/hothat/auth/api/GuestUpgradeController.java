package ru.hothat.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.auth.api.dto.UpgradeGuestAccountRequestDTO;
import ru.hothat.auth.api.dto.UpgradedGuestAccountResponseDTO;
import ru.hothat.auth.usecase.UpgradeGuestAccountUseCase;
import ru.hothat.config.HotHatUser;

/**
 * Апгрейд гостевой учётной записи до полноценной.
 *
 * <p>Заменяет {@code POST /api/auth/link} и лечит его главную беду: тот был
 * открыт всем и верил полю {@code uid} в теле (аудит A4). Здесь операция
 * требует Bearer-токен гостя, а идентификатор берётся из токена — тело на
 * выбор учётки не влияет вовсе.
 *
 * <p>Отдельный класс, а не метод в {@code AccountController}, ровно поэтому:
 * у соседей уровень прав «никакого токена», а здесь — «токен гостя». Один
 * класс — один уровень прав; смешать их значило бы объявить в спецификации
 * замок для регистрации или отсутствие замка для апгрейда.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на этот маршрут отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/auth/accounts/upgrades")
@Tag(name = "Auth · апгрейд гостя", description = "Превращение гостевой учётки в полноценную")
@SecurityRequirement(name = "Bearer")
public class GuestUpgradeController {

    private final UpgradeGuestAccountUseCase upgradeGuestAccount;

    @Operation(summary = "Превратить гостя в аккаунт",
            description = "Задаёт гостевой учётке почту и пароль, сохраняя идентификатор, а с ним "
                    + "и весь прогресс. Личность гостя доказывает Bearer-токен: поля uid в теле нет "
                    + "и быть не может. Ответ 200, а не 201 — учётка существовала, изменился её род. "
                    + "Пара токенов выдаётся новая: прежняя несла роль гостя.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Гость стал полноценной учётной записью"),
            @ApiResponse(responseCode = "400", description = "INVALID_EMAIL, WEAK_PASSWORD", content = @Content),
            @ApiResponse(responseCode = "401", description = "AUTH_REQUIRED: нет токена гостя", content = @Content),
            @ApiResponse(responseCode = "403", description = "Токен принадлежит не гостю", content = @Content),
            @ApiResponse(responseCode = "409", description = "ALREADY_REGISTERED: учётка уже полноценная; "
                    + "EMAIL_TAKEN: почта занята", content = @Content)})
    @PostMapping
    public ResponseEntity<UpgradedGuestAccountResponseDTO> upgrade(
            @AuthenticationPrincipal HotHatUser guest,
            @Valid @RequestBody UpgradeGuestAccountRequestDTO request,
            @Parameter(description = "Строка клиента; сохраняется вместе с новой сессией")
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return ResponseEntity.ok(upgradeGuestAccount.run(guest, request, userAgent));
    }
}
