package ru.hothat.machine.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Дверь в {@code v2.maintenance_run_target} — что именно тронул прогон.
 *
 * <p>Ответ на «почему исчезла моя комната»: раньше причина сноса приезжала
 * вызывающему в теле ответа и нигде не оставалась.
 */
@Repository
interface MaintenanceRunTargets extends JpaRepository<MaintenanceRunTarget, MaintenanceRunTargetId> {
}
