package ru.hothat.machine.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Дверь в {@code v2.maintenance_run} — история прогонов уборки.
 *
 * <p>Истории раньше не было вовсе: у прохода по комнатам был кулдаун и был
 * ответ вызывающему, а узнать задним числом, когда уборка шла и что сделала,
 * было негде.
 */
@Repository
interface MaintenanceRuns extends JpaRepository<MaintenanceRun, Long> {
}
