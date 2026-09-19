package ru.hothat.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.auth.api.dto.CompletePasswordResetRequestDTO;
import ru.hothat.auth.api.dto.PasswordResetAcceptedResponseDTO;
import ru.hothat.auth.api.dto.RequestPasswordResetRequestDTO;
import ru.hothat.auth.usecase.CompletePasswordResetUseCase;
import ru.hothat.auth.usecase.RequestPasswordResetUseCase;

/**
 * Восстановление пароля: заявка и её завершение.
 *
 * <p>Заменяет {@code POST /api/auth/password-reset} и
 * {@code /password-reset/confirm}. Обе операции открыты: у того, кто забыл
 * пароль, сессии нет по определению, — поэтому у класса пустой
 * {@link SecurityRequirements} (замечание C11 аудита). Личность доказывает
 * одноразовый токен из письма.
 *
 * <p>Завершение названо под-ресурсом {@code /completions}, а не действием
 * {@code /confirm}: заявка на восстановление — предмет, завершение — второй
 * предмет, который к ней приписан.
 *
 * <p>Старые адреса пока живы: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/auth/password-resets")
@Tag(name = "Auth · восстановление пароля", description = "Заявка на восстановление и новый пароль по ссылке")
@SecurityRequirements
public class PasswordResetController {

    private final RequestPasswordResetUseCase requestPasswordReset;
    private final CompletePasswordResetUseCase completePasswordReset;

    @Operation(summary = "Запросить восстановление",
            description = "Ответ одинаков для зарегистрированного и незнакомого адреса — и по коду, "
                    + "и по телу: иначе форма превращается в проверялку, есть ли такой пользователь. "
                    + "Код 202, потому что сервер обещает попробовать, а не сообщает, что письмо ушло. "
                    + "ВНИМАНИЕ: почтовый отправитель к этой возможности пока не подключён, письмо "
                    + "не уходит; в журнал ссылка намеренно не пишется.")
    @ApiResponse(responseCode = "202", description = "Заявка принята")
    @PostMapping
    public ResponseEntity<PasswordResetAcceptedResponseDTO> request(
            @Valid @RequestBody RequestPasswordResetRequestDTO request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(requestPasswordReset.run(request));
    }

    @Operation(summary = "Задать новый пароль по ссылке",
            description = "Токен одноразовый и живёт час. Успешная смена гасит все прежние сессии "
                    + "и поднимает поколение токенов — то есть выкидывает и того, кто пароль подобрал.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Пароль изменён, прежние сессии закрыты"),
            @ApiResponse(responseCode = "400", description = "INVALID_RESET_TOKEN: ссылка недействительна "
                    + "или устарела; WEAK_PASSWORD: слишком короткий пароль", content = @Content)})
    @PostMapping("/completions")
    public ResponseEntity<Void> complete(@Valid @RequestBody CompletePasswordResetRequestDTO request) {
        completePasswordReset.run(request);
        return ResponseEntity.noContent().build();
    }
}
