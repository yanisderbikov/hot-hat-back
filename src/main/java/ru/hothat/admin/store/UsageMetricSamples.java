package ru.hothat.admin.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Метрики снимков в таблице {@code v2.usage_metric_sample}.
 *
 * <p>Не публичный: дверь области наружу одна — {@link UsageStore}.
 *
 * <p>Метрики читаются пачкой на всю страницу истории, а не по снимку в цикле:
 * страница — это до ста двадцати снимков по четырнадцать метрик, и поштучное
 * чтение стоило бы ста двадцати запросов вместо одного.
 */
@Repository
interface UsageMetricSamples extends JpaRepository<UsageMetricSample, UsageMetricSampleId> {

    List<UsageMetricSample> findBySnapshotIdIn(Collection<Long> snapshotIds);
}
