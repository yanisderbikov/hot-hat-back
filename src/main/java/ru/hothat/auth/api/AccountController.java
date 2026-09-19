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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.auth.api.dto.NicknameAvailabilityQueryDTO;
import ru.hothat.auth.api.dto.NicknameAvailabilityResponseDTO;
import ru.hothat.auth.api.dto.RegisterAccountRequestDTO;
import ru.hothat.auth.api.dto.RegisteredAccountResponseDTO;
import ru.hothat.auth.usecase.CheckNicknameAvailabilityUseCase;
import ru.hothat.auth.usecase.RegisterAccountUseCase;

/**
 * Учётная запись как ресурс: создание и подсказка занятости ника.
 *
 * <p>Заменяет {@code POST /api/auth/register} и {@code GET /api/nickname-available}
 * — тот жил в {@code PublicController} и ходил в репозиторий прямо из
 * контроллера.
 *
 * <p>Обе операции открыты и обе принадлежат одному экрану — форме регистрации:
 * подсказка нужна до того, как учётка появится, и требовать для неё токен
 * значит требовать войти, чтобы зарегистрироваться. Поэтому у класса пустой
 * {@link SecurityRequirements} (замечание C11 аудита).
 *
 * <p>Апгрейд гостя лежит глубже, под {@code /accounts/upgrades}, и в другом
 * классе: у него другой уровень прав — предъявленный токен гостя.
 *
 * <p>Старые адреса пока живы: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/auth/accounts")
@Tag(name = "Auth · учётные записи", description = "Регистрация и проверка ника")
@SecurityRequirements
public class AccountController {

    private final RegisterAccountUseCase registerAccount;
    private final CheckNicknameAvailabilityUseCase checkNicknameAvailability;

    @Operation(summary = "Зарегистрироваться",
            description = "Заводит учётную запись и сразу открывает сессию: заставлять только что "
                    + "зарегистрировавшегося вводить пароль второй раз незачем. Дивизион здесь не "
                    + "выбирается — у него свой адрес в области профиля, и выбор необратим.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Учётная запись создана, сессия открыта"),
            @ApiResponse(responseCode = "400", description = "INVALID_EMAIL, WEAK_PASSWORD, INVALID_NICKNAME",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "EMAIL_TAKEN: почта занята; "
                    + "NICKNAME_TAKEN: ник занят", content = @Content)})
    @PostMapping
    public ResponseEntity<RegisteredAccountResponseDTO> register(
            @Valid @RequestBody RegisterAccountRequestDTO request,
            @Parameter(description = "Строка клиента; сохраняется вместе с сессией")
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registerAccount.run(request, userAgent));
    }

    @Operation(summary = "Свободен ли ник",
            description = "Подсказка формы регистрации, отвечает на каждое нажатие клавиши. Поэтому "
                    + "недописанный ник — не ошибка запроса: он приходит с valid=false, а не с 400. "
                    + "Правило ника названо в описании параметра, чтобы клиенту не приходилось "
                    + "угадывать его по ответам.")
    @ApiResponse(responseCode = "200", description = "Ник проверен")
    @GetMapping("/nickname-availability")
    public ResponseEntity<NicknameAvailabilityResponseDTO> nicknameAvailability(
            @ParameterObject @Valid NicknameAvailabilityQueryDTO query) {
        return ResponseEntity.ok(checkNicknameAvailability.run(query));
    }
}
