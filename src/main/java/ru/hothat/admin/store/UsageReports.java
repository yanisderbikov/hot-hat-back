package ru.hothat.admin.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

/**
 * Ежедневные отчёты о расходе, таблица {@code v2.usage_report}.
 *
 * <p>Не публичный: дверь области наружу одна — {@link UsageStore}. Ключ —
 * сама дата, поэтому «отчёт за сегодня уже готовили» — это чтение по
 * первичному ключу, а не запрос с условием.
 */
@Repository
interface UsageReports extends JpaRepository<UsageDailyReport, LocalDate> {
}
