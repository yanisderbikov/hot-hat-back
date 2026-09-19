package ru.hothat.machine.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.machine.api.dto.RecordingSweepResponseDTO;
import ru.hothat.machine.api.dto.RoomSweepResponseDTO;
import ru.hothat.machine.api.dto.TokenSweepResponseDTO;
import ru.hothat.machine.security.MachineSecurityConfig;
import ru.hothat.machine.usecase.SweepAbandonedRoomsUseCase;
import ru.hothat.machine.usecase.SweepExpiredRecordingsUseCase;
import ru.hothat.machine.usecase.SweepExpiredTokensUseCase;

/**
 * Выполняет плановую уборку по расписанию.
 *
 * <p>Заменяет {@code /api/cleanup-rooms} и {@code /api/cleanup-recordings},
 * объявленные сегодня как {@code @RequestMapping(method={GET, POST})} — то есть
 * сносящие комнаты и удаляющие файлы из бакета по обычному GET. Здесь только
 * POST: прогон уборки — это действие, а не чтение.
 *
 * <p>Три прогона, а не один: у них разный предмет, разная стоимость и разное
 * расписание. Точечная уборка одной комнаты по {@code room_id} сюда не
 * переехала — её звал клиент при выходе из комнаты, и это работа области
 * комнаты.
 *
 * <p>Старые адреса пока живы: на них настроен внешний планировщик.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/machine/maintenance")
@Tag(name = "Machine · уборка", description = "Плановая уборка по расписанию")
@SecurityRequirement(name = MachineSecurityConfig.SECRET_SCHEME)
public class MaintenanceSweepController {

    private final SweepAbandonedRoomsUseCase sweepRooms;
    private final SweepExpiredRecordingsUseCase sweepRecordings;
    private final SweepExpiredTokensUseCase sweepTokens;

    @Operation(summary = "Убрать брошенные комнаты",
            description = "Сносит комнаты, где не осталось живых людей. У прогона есть кулдаун "
                    + "в пять минут; если он не истёк, ответ придёт со state=SKIPPED_COOLDOWN.")
    @ApiResponse(responseCode = "200", description = "Прогон выполнен или пропущен по кулдауну")
    @PostMapping("/room-sweeps")
    public ResponseEntity<RoomSweepResponseDTO> rooms() {
        return ResponseEntity.ok(sweepRooms.run());
    }

    @Operation(summary = "Удалить записи с истёкшим сроком",
            description = "Удаляет объект из бакета и помечает запись удалённой. Сохранённые "
                    + "кем-то записи не удаляются даже после истечения срока.")
    @ApiResponse(responseCode = "200", description = "Прогон выполнен")
    @PostMapping("/recording-sweeps")
    public ResponseEntity<RecordingSweepResponseDTO> recordings() {
        return ResponseEntity.ok(sweepRecordings.run());
    }

    @Operation(summary = "Удалить протухшие токены",
            description = "Чистит refresh-токены и ссылки восстановления пароля со сроком в прошлом. "
                    + "Уборка была написана и ни разу не вызвана — таблицы росли без предела.")
    @ApiResponse(responseCode = "200", description = "Прогон выполнен")
    @PostMapping("/token-sweeps")
    public ResponseEntity<TokenSweepResponseDTO> tokens() {
        return ResponseEntity.ok(sweepTokens.run());
    }
}
