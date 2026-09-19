package ru.hothat.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.admin.api.dto.AdminUsageSnapshotResponseDTO;
import ru.hothat.admin.api.dto.UsageSnapshotPageResponseDTO;
import ru.hothat.admin.api.dto.UsageSnapshotQueryDTO;
import ru.hothat.admin.usecase.ListUsageSnapshotsUseCase;
import ru.hothat.admin.usecase.TakeUsageSnapshotByAdminUseCase;
import ru.hothat.config.HotHatUser;

/**
 * Снимки расхода внешних сервисов и своей машины.
 *
 * <p>Заменяет {@code GET} и {@code POST /api/monitor} — маршрут, лежавший в
 * {@code permitAll} с проверкой администратора строкой внутри метода (A12).
 * Машинный вход агента мониторинга по секрету остаётся отдельным адресом в
 * {@code /api/v2/machine}: у него другое право входа, другой набор
 * последствий и другой ответ.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/admin/usage-snapshots")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin · снимки расхода", description = "История расхода ресурсов")
@SecurityRequirement(name = "Bearer")
public class AdminUsageSnapshotController {

    private final ListUsageSnapshotsUseCase listSnapshots;
    private final TakeUsageSnapshotByAdminUseCase takeSnapshot;

    @Operation(summary = "Показать историю снимков",
            description = "Границы задаются вдвоём или никак. Самый свежий снимок приходит "
                    + "отдельным полем и не зависит от диапазона: состояние служб на экране "
                    + "должно оставаться текущим, пока человек листает историю.")
    @GetMapping
    public ResponseEntity<UsageSnapshotPageResponseDTO> history(
            @AuthenticationPrincipal HotHatUser admin,
            @ParameterObject @Valid UsageSnapshotQueryDTO query) {
        return ResponseEntity.ok(listSnapshots.run(admin, query));
    }

    @Operation(summary = "Снять снимок сейчас",
            description = "Опрашивает сайт, API, базу и машину и сохраняет снимок за сегодняшний "
                    + "день. Писем не шлёт: тревоги по порогам рассылает плановый снимок агента.")
    @ApiResponse(responseCode = "201", description = "Снимок снят и сохранён")
    @PostMapping
    public ResponseEntity<AdminUsageSnapshotResponseDTO> take(@AuthenticationPrincipal HotHatUser admin) {
        return ResponseEntity.status(HttpStatus.CREATED).body(takeSnapshot.run(admin));
    }
}
