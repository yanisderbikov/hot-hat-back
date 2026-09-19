package ru.hothat.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.auth.api.dto.IssuedGuestSessionResponseDTO;
import ru.hothat.auth.api.dto.IssuedSessionResponseDTO;
import ru.hothat.auth.api.dto.OpenGuestSessionRequestDTO;
import ru.hothat.auth.api.dto.OpenSessionRequestDTO;
import ru.hothat.auth.api.dto.RenewSessionRequestDTO;
import ru.hothat.auth.api.dto.RenewedSessionResponseDTO;
import ru.hothat.auth.api.dto.RevokeSessionRequestDTO;
import ru.hothat.auth.usecase.OpenGuestSessionUseCase;
import ru.hothat.auth.usecase.OpenSessionUseCase;
import ru.hothat.auth.usecase.RenewSessionUseCase;
import ru.hothat.auth.usecase.RevokeSessionUseCase;

/**
 * Сессия как ресурс: открыть, открыть гостевую, обновить, закрыть.
 *
 * <p>Заменяет {@code POST /api/auth/login}, {@code /guest}, {@code /refresh}
 * и {@code /logout}. Четыре операции в одном классе, потому что уровень прав
 * у них общий и особенный: токена нет ни у одной. Вход доказывает личность
 * паролем, обновление и выход — предъявленным refresh-токеном; ни то, ни
 * другое не читает заголовок {@code Authorization}, поэтому у класса стоит
 * пустой {@link SecurityRequirements} — в Swagger UI у этих операций не должно
 * быть замка, и «Try it out» обязан работать без токена (замечание C11
 * аудита).
 *
 * <p>Всё, что делается уже с сессией на руках, живёт под {@code /me} и в
 * другом классе: там другой уровень прав.
 *
 * <p>Старые адреса пока живы: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/auth/sessions")
@Tag(name = "Auth · сессии", description = "Вход, гостевой вход, обновление пары токенов и выход")
@SecurityRequirements
public class SessionController {

    /**
     * User-Agent объявлен параметром, хотя браузер шлёт его сам: сервер
     * запоминает его в строке refresh-токена, и это единственное, чем список
     * своих сессий будет отличать телефон от ноутбука.
     */
    private static final String AGENT = "Строка клиента; сохраняется вместе с сессией";

    private final OpenSessionUseCase openSession;
    private final OpenGuestSessionUseCase openGuestSession;
    private final RenewSessionUseCase renewSession;
    private final RevokeSessionUseCase revokeSession;

    @Operation(summary = "Войти по почте и паролю",
            description = "Открывает сессию и сразу отдаёт учётную запись, чтобы экран после входа "
                    + "не делал второй запрос. Отказ одинаков для незнакомой почты и неверного "
                    + "пароля — иначе форма входа перечисляет зарегистрированные адреса.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Сессия открыта"),
            @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS: неверная почта или пароль",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "USER_BANNED: учётка заблокирована",
                    content = @Content)})
    @PostMapping
    public ResponseEntity<IssuedSessionResponseDTO> open(
            @Valid @RequestBody OpenSessionRequestDTO request,
            @Parameter(description = AGENT) @RequestHeader(value = HttpHeaders.USER_AGENT, required = false)
            String userAgent) {
        return ResponseEntity.status(HttpStatus.CREATED).body(openSession.run(request, userAgent));
    }

    @Operation(summary = "Войти гостем",
            description = "Ни почты, ни пароля. Тело можно не присылать вовсе — тогда сервер придумает "
                    + "свободный ник Guest######. Токен несёт роль гостя: с ней открыты лобби, превью "
                    + "комнаты, апгрейд учётки и правовые согласия, и ничего сверх.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Гостевая сессия открыта"),
            @ApiResponse(responseCode = "409", description = "NICKNAME_TAKEN: запрошенный ник занят",
                    content = @Content)})
    @PostMapping("/guest")
    public ResponseEntity<IssuedGuestSessionResponseDTO> openGuest(
            @Valid @RequestBody(required = false) OpenGuestSessionRequestDTO request,
            @Parameter(description = AGENT) @RequestHeader(value = HttpHeaders.USER_AGENT, required = false)
            String userAgent) {
        return ResponseEntity.status(HttpStatus.CREATED).body(openGuestSession.run(request, userAgent));
    }

    @Operation(summary = "Обновить пару токенов",
            description = "Меняет refresh-токен на новую пару: прежний гасится, поэтому украденный "
                    + "токен годится ровно на один обмен. Заодно отдаёт учётную запись — за срок "
                    + "жизни access-токена она могла измениться с другого устройства.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Пара обновлена"),
            @ApiResponse(responseCode = "401", description = "INVALID_REFRESH_TOKEN: токен неизвестен, "
                    + "просрочен или уже погашен", content = @Content),
            @ApiResponse(responseCode = "403", description = "USER_BANNED: учётка заблокирована",
                    content = @Content)})
    @PostMapping("/renewal")
    public ResponseEntity<RenewedSessionResponseDTO> renew(
            @Valid @RequestBody RenewSessionRequestDTO request,
            @Parameter(description = AGENT) @RequestHeader(value = HttpHeaders.USER_AGENT, required = false)
            String userAgent) {
        return ResponseEntity.ok(renewSession.run(request, userAgent));
    }

    @Operation(summary = "Выйти с текущего устройства",
            description = "Гасит предъявленный refresh-токен. Идемпотентно и молчаливо: незнакомый, "
                    + "чужой и уже погашенный токен дают тот же 204 — иначе адресом можно проверять "
                    + "чужие токены на существование. Access-токен доживает свой срок; чтобы оборвать "
                    + "доступ немедленно, есть выход со всех устройств.")
    @ApiResponse(responseCode = "204", description = "Сессия закрыта")
    @DeleteMapping("/current")
    public ResponseEntity<Void> revoke(@Valid @RequestBody RevokeSessionRequestDTO request) {
        revokeSession.run(request);
        return ResponseEntity.noContent().build();
    }
}
