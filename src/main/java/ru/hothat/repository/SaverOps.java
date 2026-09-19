package ru.hothat.repository;

import ru.hothat.model.ops.MaintenanceState;
import ru.hothat.model.ops.UsageAlert;
import ru.hothat.model.ops.UsageDaily;
import ru.hothat.model.ops.UsageMailCounter;
import ru.hothat.model.ops.UsageMonitorState;
import ru.hothat.model.ops.UsageMonthly;
import ru.hothat.model.ops.UsageReport;

public interface SaverOps {


    UsageDaily saveUsageDay(UsageDaily day);

    UsageMonthly saveUsageMonth(UsageMonthly month);

    UsageAlert saveAlert(UsageAlert alert);

    UsageReport saveReport(UsageReport report);

    UsageMailCounter saveMailCounter(UsageMailCounter counter);

    UsageMonitorState saveMonitorState(UsageMonitorState state);

    MaintenanceState saveMaintenance(MaintenanceState state);
}
