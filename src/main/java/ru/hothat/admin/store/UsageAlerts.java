package ru.hothat.admin.store;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Тревоги о превышении порога в таблице {@code v2.usage_alert}.
 *
 * <p>Не публичный: дверь области наружу одна — {@link UsageStore}.
 *
 * <p>Поиск идёт по четырём машинным колонкам — ровно по тем, что стоят в
 * уникальном индексе {@code ux_usage_alert_period_metric}. Подписи в поиске
 * нет намеренно: раньше опознавали по ней, и переименование подписи слало
 * второе письмо о том же превышении.
 */
@Repository
interface UsageAlerts extends JpaRepository<UsageThresholdAlert, Long> {

    Optional<UsageThresholdAlert> findByPeriodKindAndPeriodKeyAndMetricKeyAndThreshold(
            String periodKind, String periodKey, String metricKey, Short threshold);

    /** Лента предупреждений в админке: свежие сверху. */
    List<UsageThresholdAlert> findAllByOrderByCreatedAtDesc(Pageable page);
}
