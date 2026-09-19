package ru.hothat.repository;

import ru.hothat.model.ops.MaintenanceState;
import ru.hothat.model.ops.UsageAlert;
import ru.hothat.model.ops.UsageDaily;
import ru.hothat.model.ops.UsageMailCounter;
import ru.hothat.model.ops.UsageMonitorState;
import ru.hothat.model.ops.UsageMonthly;
import ru.hothat.model.ops.UsageReport;


import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GetterOps {

    /** Переключатель возможности; пусто, если такого имени в базе нет. */



    List<UsageDaily> getUsageDays(String start, String end, int limit);

    Optional<UsageDaily> getUsageDay(String date);

    Optional<UsageMonthly> getUsageMonth(String month);

    List<UsageAlert> getRecentAlerts(int limit);

    Optional<UsageAlert> getAlert(String id);

    Optional<UsageReport> getReport(String date);

    Optional<UsageMailCounter> getMailCounter(String periodKey);

    Optional<UsageMonitorState> getMonitorState(String id);

    Optional<MaintenanceState> getMaintenance(String id);
}
