package ru.hothat.machine.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.machine.api.dto.MachineUsageSnapshotResponseDTO;
import ru.hothat.machine.security.MachineSecurityConfig;
import ru.hothat.machine.usecase.TakeUsageSnapshotByAgentUseCase;

/**
 * Принимает снимок расхода от агента мониторинга.
 *
 * <p>Заменяет ту половину {@code POST /api/monitor}, которая удостоверялась
 * секретом {@code X-Hot-Hat-Monitor-Secret}. Вторая половина — «снять сейчас»
 * из консоли администратора — остаётся в области консоли: у неё другой
 * читатель, другой экран и другой ответ.
 *
 * <p>Старый адрес пока жив: на него настроен внешний агент.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/machine/usage-snapshots")
@Tag(name = "Machine · снимки расхода", description = "Плановые снимки расхода ресурсов")
@SecurityRequirement(name = MachineSecurityConfig.SECRET_SCHEME)
public class UsageSnapshotAgentController {

    private final TakeUsageSnapshotByAgentUseCase takeUsageSnapshot;

    @Operation(summary = "Снять снимок расхода",
            description = "Меряет расход, сохраняет снимок за сутки и шлёт письма о превышении "
                    + "порогов. Вне часов планового снимка ничего не мерит и отвечает "
                    + "state=SKIPPED_OUTSIDE_WINDOW: агент может дёргать адрес хоть каждый час, "
                    + "снимков всё равно будет четыре в сутки.")
    @ApiResponse(responseCode = "201", description = "Снимок снят или пропущен вне окна")
    @PostMapping
    public ResponseEntity<MachineUsageSnapshotResponseDTO> take() {
        return ResponseEntity.status(HttpStatus.CREATED).body(takeUsageSnapshot.run());
    }
}
