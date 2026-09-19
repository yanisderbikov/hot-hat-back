package ru.hothat.machine.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.admin.spi.UsageSnapshotPort;
import ru.hothat.machine.api.dto.MachineUsageSnapshotResponseDTO;
import ru.hothat.machine.api.dto.UsageSnapshotState;

/**
 * Снять показания расхода по приходу агента наблюдения.
 *
 * <p>Окно, в которое это позволено, считает область наблюдения: у машины нет
 * ни порогов, ни расписания, ни почты. Её дело — удостоверить агента и
 * передать повод дальше, через дверь владельца снимков
 * ({@link UsageSnapshotPort}).
 */
@Service
@RequiredArgsConstructor
public class TakeUsageSnapshotByAgentUseCase {

    private final UsageSnapshotPort usage;

    @PreAuthorize("hasRole('MONITOR_AGENT')")
    public MachineUsageSnapshotResponseDTO run() {
        if (!usage.scheduledWindowAllowed()) {
            return new MachineUsageSnapshotResponseDTO(
                    UsageSnapshotState.SKIPPED_OUTSIDE_WINDOW, null, 0, false);
        }
        UsageSnapshotPort.Taken taken = usage.takeScheduled();
        return new MachineUsageSnapshotResponseDTO(UsageSnapshotState.TAKEN,
                taken.collectedAt().toString(), taken.alertsRaised(), taken.reportDelivered());
    }
}
