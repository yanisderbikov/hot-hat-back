package ru.hothat.machine.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Дверь в {@code v2.maintenance_lease}; за пределы машинной области не выходит. */
@Repository
interface MaintenanceLeases extends JpaRepository<MaintenanceLease, String> {
}
