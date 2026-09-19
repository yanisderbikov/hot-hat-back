package ru.hothat.admin.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Проверки живости в таблице {@code v2.service_health_probe}.
 *
 * <p>Не публичный: дверь области наружу одна — {@link UsageStore}. Читаются
 * пачкой на всю страницу истории по той же причине, что и метрики.
 */
@Repository
interface ServiceHealthProbes extends JpaRepository<ServiceHealthProbe, ServiceHealthProbeId> {

    List<ServiceHealthProbe> findBySnapshotIdIn(Collection<Long> snapshotIds);
}
