package ru.hothat.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.admin.api.dto.AdminUserPageResponseDTO;
import ru.hothat.admin.api.dto.AdminUserQueryDTO;
import ru.hothat.admin.usecase.ListRegisteredUsersUseCase;
import ru.hothat.config.HotHatUser;

/**
 * Реестр учётных записей для владельца сервиса.
 *
 * <p>Отдельный класс, а не метод в дашборде, потому что право другое: это
 * единственный адрес админской консоли, где нужен владелец, а не
 * администратор, — здесь наружу идёт почта чужих аккаунтов. Держать его рядом
 * с комнатами значило бы иметь на классе две схемы прав.
 *
 * <p>Заменяет {@code POST /api/portal} с {@code action=list_users}, который
 * лежал под общим матчером {@code authenticated} и проверял владельца
 * сравнением почты внутри сервиса (A6).
 *
 * <p>Старый адрес пока жив: фронтенд переедет на этот маршрут отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/admin/users")
@PreAuthorize("hasRole('OWNER')")
@Tag(name = "Admin · учётки", description = "Реестр учётных записей")
@SecurityRequirement(name = "Bearer")
public class OwnerUserRegistryController {

    private final ListRegisteredUsersUseCase listUsers;

    @Operation(summary = "Показать учётные записи",
            description = "Страница реестра: ник, почта, дивизион и дата регистрации. "
                    + "Гости и свой аккаунт в список не попадают.")
    @GetMapping
    public ResponseEntity<AdminUserPageResponseDTO> list(
            @AuthenticationPrincipal HotHatUser owner,
            @ParameterObject @Valid AdminUserQueryDTO query) {
        return ResponseEntity.ok(listUsers.run(owner, query));
    }
}
