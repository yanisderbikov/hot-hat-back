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
import ru.hothat.admin.api.dto.ConfigurationReadinessResponseDTO;
import ru.hothat.admin.api.dto.HostMetricsResponseDTO;
import ru.hothat.admin.api.dto.LiveRoomDashboardResponseDTO;
import ru.hothat.admin.api.dto.LiveRoomQueryDTO;
import ru.hothat.admin.api.dto.ObjectStorageConfigResponseDTO;
import ru.hothat.admin.api.dto.UsageStatisticsQueryDTO;
import ru.hothat.admin.api.dto.UsageStatisticsResponseDTO;
import ru.hothat.admin.usecase.CheckConfigurationReadinessUseCase;
import ru.hothat.admin.usecase.GetHostMetricsUseCase;
import ru.hothat.admin.usecase.GetLiveRoomDashboardUseCase;
import ru.hothat.admin.usecase.GetObjectStorageConfigUseCase;
import ru.hothat.admin.usecase.GetUsageStatisticsUseCase;
import ru.hothat.config.HotHatUser;

/**
 * Сводки о состоянии сервиса для администратора.
 *
 * <p>Заменяет {@code GET /api/admin?scope=…} — один адрес, который отдавал три
 * разных ответа в зависимости от слова в запросе, причём слово {@code all}
 * склеивало два из них в один объект с общим ключом {@code stats}. Клиенту это
 * не помогало: за расходом ресурсов он всё равно шёл вторым запросом на
 * {@code /api/monitor} ({@code admin.js:271}). Здесь пять предметов — пять
 * адресов, и каждый отвечает одной формой.
 *
 * <p>Старые адреса пока живы: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin · дашборд", description = "Сводки о состоянии сервиса")
@SecurityRequirement(name = "Bearer")
public class AdminDashboardController {

    private final GetLiveRoomDashboardUseCase getLiveRooms;
    private final GetUsageStatisticsUseCase getUsageStatistics;
    private final GetHostMetricsUseCase getHostMetrics;
    private final CheckConfigurationReadinessUseCase checkConfiguration;
    private final GetObjectStorageConfigUseCase getObjectStorage;

    @Operation(summary = "Показать живые комнаты",
            description = "Открытые комнаты с составами и четыре счётчика текущей нагрузки. "
                    + "Счётчики считаются по этой же выборке и потому едут вместе с ней.")
    @GetMapping("/live-rooms")
    public ResponseEntity<LiveRoomDashboardResponseDTO> liveRooms(
            @AuthenticationPrincipal HotHatUser admin,
            @ParameterObject @Valid LiveRoomQueryDTO query) {
        return ResponseEntity.ok(getLiveRooms.run(admin, query));
    }

    @Operation(summary = "Показать статистику за период",
            description = "Пустой диапазон — последняя неделя, перевёрнутый разворачивается, "
                    + "слишком длинный обрезается. Период, за который посчитан ответ, "
                    + "возвращается вместе с числами.")
    @GetMapping("/usage")
    public ResponseEntity<UsageStatisticsResponseDTO> usage(
            @AuthenticationPrincipal HotHatUser admin,
            @ParameterObject @Valid UsageStatisticsQueryDTO query) {
        return ResponseEntity.ok(getUsageStatistics.run(admin, query));
    }

    @Operation(summary = "Показать метрики машины",
            description = "Диск, память, сеть, загрузка и службы прямо сейчас. "
                    + "На не-Linux отвечает той же формой с supported=false.")
    @GetMapping("/host-metrics")
    public ResponseEntity<HostMetricsResponseDTO> hostMetrics(@AuthenticationPrincipal HotHatUser admin) {
        return ResponseEntity.ok(getHostMetrics.run(admin));
    }

    @Operation(summary = "Показать готовность окружения",
            description = "Какие переменные окружения заданы. Значений не отдаёт — только признак.")
    @GetMapping("/configuration")
    public ResponseEntity<ConfigurationReadinessResponseDTO> configuration(
            @AuthenticationPrincipal HotHatUser admin) {
        return ResponseEntity.ok(checkConfiguration.run(admin));
    }

    @Operation(summary = "Показать настройки файлового хранилища",
            description = "Ненастроенное хранилище — это ответ 200 с configured=false, а не ошибка.")
    @GetMapping("/object-storage")
    public ResponseEntity<ObjectStorageConfigResponseDTO> objectStorage(
            @AuthenticationPrincipal HotHatUser admin) {
        return ResponseEntity.ok(getObjectStorage.run(admin));
    }
}
