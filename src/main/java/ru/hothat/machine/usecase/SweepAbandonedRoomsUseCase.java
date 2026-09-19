package ru.hothat.machine.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.machine.api.dto.RoomSweepResponseDTO;
import ru.hothat.machine.api.dto.SweepState;
import ru.hothat.machine.api.dto.SweptRoomView;
import ru.hothat.machine.store.MaintenanceJournal;
import ru.hothat.room.spi.RoomJanitorPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Проход по брошенным комнатам.
 *
 * <p>Двойная защита от одновременных прогонов — не перестраховка. Аренда
 * ({@code maintenance_lease}) не даёт двум узлам начать проход разом: раньше
 * правило кулдауна читало, решало и писало тремя шагами, и два планировщика
 * проходили его оба. Кулдаун самого движка уборки остаётся, пока правила
 * комнаты живут там же, где жили.
 *
 * <p>Что именно убрано и почему, теперь остаётся в журнале. Раньше причина
 * сноса приезжала вызывающему в теле ответа и нигде не оставалась — на вопрос
 * «почему исчезла моя комната» ответить было нечем.
 *
 * <p>Убирает комнаты их собственная область: {@link RoomJanitorPort} — её
 * дверь, и правило «кого считать брошенным» машине не показывается вовсе.
 */
@Service
@RequiredArgsConstructor
public class SweepAbandonedRoomsUseCase {

    private final RoomJanitorPort rooms;
    private final MaintenanceJournal journal;

    @PreAuthorize("hasRole('CRON')")
    public RoomSweepResponseDTO run() {
        Optional<Long> run = journal.begin(MaintenanceJournal.ROOM_SWEEP, "cron");
        if (run.isEmpty()) {
            // Уборка уже идёт у соседа: второй проход по тем же комнатам не
            // нужен и вреден.
            return new RoomSweepResponseDTO(SweepState.SKIPPED_COOLDOWN, 0, 0, List.of());
        }
        long runId = run.get();
        RoomJanitorPort.Sweep report;
        try {
            report = rooms.sweep();
        } catch (RuntimeException e) {
            journal.failed(runId, e.getMessage());
            throw e;
        }
        if (report.skipped()) {
            journal.skippedCooldown(runId);
            return new RoomSweepResponseDTO(SweepState.SKIPPED_COOLDOWN, 0, 0, List.of());
        }
        List<SweptRoomView> swept = new ArrayList<>(report.rooms().size());
        List<MaintenanceJournal.Target> touched = new ArrayList<>(report.rooms().size());
        for (RoomJanitorPort.Swept room : report.rooms()) {
            swept.add(new SweptRoomView(room.roomId(), room.reason()));
            touched.add(new MaintenanceJournal.Target(MaintenanceJournal.TARGET_ROOM,
                    room.roomId(), MaintenanceJournal.OUTCOME_DELETED, room.reason()));
        }
        // Убранные комнаты и есть удалённые: список ведёт та же область, что
        // и удаляет, и второго счётчика ей держать незачем.
        int deleted = report.rooms().size();
        journal.succeeded(runId, report.checked(), deleted, touched);
        return new RoomSweepResponseDTO(SweepState.EXECUTED, report.checked(), deleted, swept);
    }
}
