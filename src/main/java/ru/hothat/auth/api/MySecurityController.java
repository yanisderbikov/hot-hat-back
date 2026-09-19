package ru.hothat.auth.api;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.auth.api.dto.ChangeOwnPasswordRequestDTO;
import ru.hothat.auth.api.dto.RevokedSessionsResponseDTO;
import ru.hothat.auth.usecase.ChangeOwnPasswordUseCase;
import ru.hothat.auth.usecase.RevokeAllSessionsUseCase;
import ru.hothat.config.HotHatUser;

/**
 * Безопасность своей учётки: пароль и чужие сессии.
 *
 * <p>Заменяет {@code POST /api/auth/password} и {@code POST /api/auth/logout-all}
 * — обе операции в старом контроллере шли без {@code @SecurityRequirement},
 * и в Swagger UI у них не было замка, хотя без токена они не работают
 * (замечание C11 аудита).
 *
 * <p>Уровень прав один: {@code hasRole('USER')}. Гостя здесь нет по существу —
 * пароля у него не заведено, а сессия одна.
 *
 * <p>Пароль — под-ресурс {@code security/password} и метод {@code PUT}:
 * значение заменяется целиком, повтор того же запроса ничего не портит.
 * Сессии — коллекция под {@code security/sessions}, и {@code DELETE} по ней
 * означает ровно то, что написано: снести их все.
 *
 * <p>Старые адреса пока живы: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/auth/me/security")
@Tag(name = "Auth · безопасность учётки", description = "Смена своего пароля и выход со всех устройств")
@SecurityRequirement(name = "Bearer")
public class MySecurityController {

    private final ChangeOwnPasswordUseCase changeOwnPassword;
    private final RevokeAllSessionsUseCase revokeAllSessions;

    @Operation(summary = "Сменить свой пароль",
            description = "Требует текущий пароль. Неверный текущий — это 403 PASSWORD_MISMATCH, "
                    + "а не 401: сессия жива, и уводить человека на экран входа посреди смены пароля "
                    + "неправильно. Успех гасит все прежние сессии, включая эту, и поднимает поколение "
                    + "токенов — если пароль меняют из-за кражи, подобравший вылетает сразу.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Пароль изменён, прежние сессии закрыты"),
            @ApiResponse(responseCode = "400", description = "WEAK_PASSWORD: новый пароль короче шести знаков",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "PASSWORD_MISMATCH: текущий пароль неверен",
                    content = @Content)})
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal HotHatUser user,
                                               @Valid @RequestBody ChangeOwnPasswordRequestDTO request) {
        changeOwnPassword.run(user, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Выйти со всех устройств",
            description = "Отзывает все refresh-токены и поднимает поколение токенов, поэтому выданные "
                    + "ранее access-токены умирают немедленно, а не доживают свои минуты. Текущее "
                    + "устройство — не исключение: клиент обязан увести человека на экран входа.")
    @ApiResponse(responseCode = "200", description = "Все сессии завершены")
    @DeleteMapping("/sessions")
    public ResponseEntity<RevokedSessionsResponseDTO> revokeAll(@AuthenticationPrincipal HotHatUser user) {
        return ResponseEntity.ok(revokeAllSessions.run(user));
    }
}
