package ru.hothat.admin.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Накопленный трафик за период, таблица {@code v2.usage_traffic_period}.
 *
 * <p>Не публичный: дверь области наружу одна — {@link UsageStore}.
 */
@Repository
interface TrafficPeriods extends JpaRepository<TrafficPeriod, TrafficPeriodId> {
}
