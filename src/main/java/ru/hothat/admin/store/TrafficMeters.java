package ru.hothat.admin.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Последнее сырое показание счётчика ОС, таблица {@code v2.usage_traffic_meter}.
 *
 * <p>Не публичный: дверь области наружу одна — {@link UsageStore}.
 */
@Repository
interface TrafficMeters extends JpaRepository<TrafficMeter, String> {
}
