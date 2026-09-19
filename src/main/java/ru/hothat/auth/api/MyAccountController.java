package ru.hothat.auth.api;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.auth.api.dto.MyAccountResponseDTO;
import ru.hothat.auth.api.dto.MyBanStateResponseDTO;
import ru.hothat.auth.usecase.GetMyAccountUseCase;
import ru.hothat.auth.usecase.GetMyBanStateUseCase;
import ru.hothat.config.HotHatUser;

/**
 * Своя учётная запись: кто я и не заблокирован ли я.
 *
 * <p>Заменяет учётную половину {@code GET /api/auth/me} и клиентскую подписку
 * на документ {@code bans/{uid}}. Профильная половина уехала в
 * {@code GET /api/v2/profile/me}: там аватар, дивизион и язык интерфейса —
 * предметы с другой судьбой и другой частотой изменений.
 *
 * <p>Уровень прав один на оба чтения и особенный для области: гость равен
 * игроку. Гостю выдал имя сервер, и не сказать ему это имя нельзя; забанить
 * гостя можно так же, как игрока, — а узнать о бане ему до сих пор было
 * неоткуда (живой канал социальных событий гостю закрыт).
 *
 * <p>Всё, что меняет учётку, лежит в соседнем классе под {@code /me/security}:
 * там гостя нет.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/auth/me")
@Tag(name = "Auth · своя учётка", description = "Своя учётная запись и состояние блокировки")
@SecurityRequirement(name = "Bearer")
public class MyAccountController {

    private final GetMyAccountUseCase getMyAccount;
    private final GetMyBanStateUseCase getMyBanState;

    @Operation(summary = "Показать свою учётку",
            description = "Идентификатор, почта, ник, род учётки и признак администратора. "
                    + "Профиля здесь нет: у него свой адрес в области профиля.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Учётная запись"),
            @ApiResponse(responseCode = "401", description = "AUTH_REQUIRED: нет токена или он истёк",
                    content = @Content)})
    @GetMapping
    public ResponseEntity<MyAccountResponseDTO> me(@AuthenticationPrincipal HotHatUser user) {
        return ResponseEntity.ok(getMyAccount.run(user));
    }

    @Operation(summary = "Заблокирован ли я",
            description = "Заменяет клиентскую подписку на bans/{uid}: страница узнавала о бане по "
                    + "появлению документа и молча выбрасывала человека. Причина названа полем — "
                    + "«вас заблокировали» без причины превращается в обращение в поддержку.")
    @ApiResponse(responseCode = "200", description = "Состояние блокировки")
    @GetMapping("/ban-state")
    public ResponseEntity<MyBanStateResponseDTO> banState(@AuthenticationPrincipal HotHatUser user) {
        return ResponseEntity.ok(getMyBanState.run(user));
    }
}
