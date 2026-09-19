package ru.hothat.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import ru.hothat.model.ops.*;
import ru.hothat.repository.GetterOps;
import ru.hothat.repository.SaverOps;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@AllArgsConstructor
@Slf4j
class OpsManager implements GetterOps, SaverOps {

    private final UsageDailyRepo usageDailyRepo;
    private final UsageMonthlyRepo usageMonthlyRepo;
    private final UsageAlertRepo usageAlertRepo;
    private final UsageReportRepo usageReportRepo;
    private final UsageMailCounterRepo usageMailCounterRepo;
    private final UsageMonitorStateRepo usageMonitorStateRepo;
    private final MaintenanceStateRepo maintenanceStateRepo;

    @Override
    public List<UsageDaily> getUsageDays(String start, String end, int limit) {
        if (start != null && !start.isBlank() && end != null && !end.isBlank()) {
            return wrap("getUsageDays",
                    () -> usageDailyRepo.findByDateBetweenOrderByDateDesc(start, end, Limit.of(limit)));
        }
        return wrap("getUsageDays", () -> usageDailyRepo.findAllByOrderByDateDesc(Limit.of(limit)));
    }

    @Override
    public Optional<UsageDaily> getUsageDay(String date) {
        return wrap("getUsageDay", () -> usageDailyRepo.findById(date));
    }

    @Override
    public Optional<UsageMonthly> getUsageMonth(String month) {
        return wrap("getUsageMonth", () -> usageMonthlyRepo.findById(month));
    }

    @Override
    public List<UsageAlert> getRecentAlerts(int limit) {
        return wrap("getRecentAlerts", () -> usageAlertRepo.findAllByOrderByCreatedAtDesc(Limit.of(limit)));
    }

    @Override
    public Optional<UsageAlert> getAlert(String id) {
        return wrap("getAlert", () -> usageAlertRepo.findById(id));
    }

    @Override
    public Optional<UsageReport> getReport(String date) {
        return wrap("getReport", () -> usageReportRepo.findById(date));
    }

    @Override
    public Optional<UsageMailCounter> getMailCounter(String periodKey) {
        return wrap("getMailCounter", () -> usageMailCounterRepo.findById(periodKey));
    }

    @Override
    public Optional<UsageMonitorState> getMonitorState(String id) {
        return wrap("getMonitorState", () -> usageMonitorStateRepo.findById(id));
    }

    @Override
    public Optional<MaintenanceState> getMaintenance(String id) {
        return wrap("getMaintenance", () -> maintenanceStateRepo.findById(id));
    }

    @Override
    public UsageDaily saveUsageDay(UsageDaily day) {
        day.setUpdatedAt(Instant.now());
        return wrap("saveUsageDay", () -> usageDailyRepo.save(day));
    }

    @Override
    public UsageMonthly saveUsageMonth(UsageMonthly month) {
        month.setUpdatedAt(Instant.now());
        return wrap("saveUsageMonth", () -> usageMonthlyRepo.save(month));
    }

    @Override
    public UsageAlert saveAlert(UsageAlert alert) {
        return wrap("saveAlert", () -> usageAlertRepo.save(alert));
    }

    @Override
    public UsageReport saveReport(UsageReport report) {
        return wrap("saveReport", () -> usageReportRepo.save(report));
    }

    @Override
    public UsageMailCounter saveMailCounter(UsageMailCounter counter) {
        counter.setUpdatedAt(Instant.now());
        return wrap("saveMailCounter", () -> usageMailCounterRepo.save(counter));
    }

    @Override
    public UsageMonitorState saveMonitorState(UsageMonitorState state) {
        state.setUpdatedAt(Instant.now());
        return wrap("saveMonitorState", () -> usageMonitorStateRepo.save(state));
    }

    @Override
    public MaintenanceState saveMaintenance(MaintenanceState state) {
        state.setUpdatedAt(Instant.now());
        return wrap("saveMaintenance", () -> maintenanceStateRepo.save(state));
    }

    private <T> T wrap(String operation, java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (Exception e) {
            log.error("OpsManager.{} failed", operation, e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
