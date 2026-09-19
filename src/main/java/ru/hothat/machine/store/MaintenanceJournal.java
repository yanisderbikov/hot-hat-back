package ru.hothat.machine.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Аренда на уборку и её журнал — единственная дверь машинной области в свои
 * три таблицы.
 *
 * <p>Аренда БЕРЁТСЯ на время, а не выводится из «когда начинали». Разница
 * видна, когда прогон падает посреди работы: отметка «начал» осталась бы
 * вечной и заперла бы уборку до ручного вмешательства, а аренда просто
 * истекает. Захват — одно условное обновление по {@code @Version}: раньше
 * правило «если прошлый прогон был меньше пяти минут назад — не начинать»
 * читало, решало и писало тремя отдельными шагами, и два планировщика,
 * запущенные одновременно, проходили его оба.
 *
 * <p>Наружу отдаются номера прогонов и записи, а не сущности.
 *
 * <p>Каждая запись журнала идёт СВОЕЙ транзакцией ({@code REQUIRES_NEW}), а не
 * частью чужой. Иначе журнал был бы бесполезен ровно там, где нужнее всего:
 * упавший прогон откатил бы вместе со своей работой и запись о том, что он
 * падал. Аренда по той же причине берётся отдельно — она обязана пережить
 * откат работы, иначе соседний узел кинулся бы делать то же самое.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceJournal {

    /** Виды уборки; набор закрыт ограничением базы. */
    public static final String ROOM_SWEEP = MaintenanceLease.ROOM_SWEEP;
    public static final String RECORDING_SWEEP = MaintenanceLease.RECORDING_SWEEP;
    public static final String TOKEN_SWEEP = MaintenanceLease.TOKEN_SWEEP;

    /** Виды объектов уборки; набор тоже закрыт базой. */
    public static final String TARGET_ROOM = MaintenanceRunTarget.ROOM;
    public static final String TARGET_RECORDING = MaintenanceRunTarget.RECORDING;
    public static final String TARGET_REFRESH_TOKEN = MaintenanceRunTarget.REFRESH_TOKEN;

    public static final String OUTCOME_DELETED = MaintenanceRunTarget.DELETED;
    public static final String OUTCOME_KEPT = MaintenanceRunTarget.KEPT;

    /** Дольше этого ни один прогон не идёт; аренда переживает падение узла. */
    private static final Duration LEASE = Duration.ofMinutes(5);

    private final MaintenanceLeases leases;
    private final MaintenanceRuns runs;
    private final MaintenanceRunTargets targets;
    private final Clock clock;

    /**
     * Взять аренду и начать прогон.
     *
     * @return номер прогона; пусто — уборка уже идёт у соседа
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Long> begin(String job, String triggeredBy) {
        Instant now = clock.instant();
        MaintenanceLease lease = leases.findById(job).orElseGet(() ->
                MaintenanceLease.builder().job(job).build());
        if (lease.getLeasedUntil() != null && lease.getLeasedUntil().isAfter(now)) {
            return Optional.empty();
        }
        lease.setLeasedUntil(now.plus(LEASE));
        lease.setLeasedBy(triggeredBy);
        lease.setLastRunAt(now);
        try {
            leases.saveAndFlush(lease);
        } catch (ObjectOptimisticLockingFailureException e) {
            // Проигравший в гонке за аренду не начинает второй проход.
            log.debug("Аренда уборки {} досталась соседу", job);
            return Optional.empty();
        }
        MaintenanceRun run = runs.saveAndFlush(MaintenanceRun.builder()
                .job(job).triggeredBy(triggeredBy).state(MaintenanceRun.RUNNING).startedAt(now).build());
        return Optional.of(run.getId());
    }

    /** Прогон дошёл до конца. Два счётчика, потому что «просмотрено» больше «тронуто». */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeeded(long runId, int checked, int affected, List<Target> touched) {
        close(runId, MaintenanceRun.SUCCEEDED, checked, affected, null, touched);
    }

    /** Прогон упал. Причина обязательна: база проверяет, что провал её назвал. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(long runId, String reason) {
        close(runId, MaintenanceRun.FAILED, 0, 0,
                reason == null || reason.isBlank() ? "sweep failed" : reason, List.of());
    }

    /** Не наш черёд: прогон записан, но ничего не делал. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void skippedCooldown(long runId) {
        close(runId, MaintenanceRun.SKIPPED_COOLDOWN, 0, 0, null, List.of());
    }

    private void close(long runId, String state, int checked, int affected,
                       String reason, List<Target> touched) {
        Instant now = clock.instant();
        MaintenanceRun run = runs.findById(runId).orElse(null);
        if (run == null) {
            return;
        }
        run.setState(state);
        run.setCheckedCount(Math.max(0, checked));
        run.setAffectedCount(Math.max(0, affected));
        run.setFailureReason(reason == null ? null : trim(reason, 400));
        run.setFinishedAt(now);
        runs.save(run);
        if (!touched.isEmpty()) {
            List<MaintenanceRunTarget> rows = new ArrayList<>(touched.size());
            for (Target target : touched) {
                rows.add(MaintenanceRunTarget.builder()
                        .runId(runId)
                        .targetKind(target.kind())
                        .targetId(trim(target.id(), 180))
                        .outcome(target.outcome())
                        .reason(trim(target.reason(), 60))
                        .at(now)
                        .build());
            }
            // Одной пачкой: сто убранных комнат — сто строк, но один заход.
            targets.saveAll(rows);
        }
        // Аренда отпускается сразу: держать её до конца срока значило бы
        // запретить следующий прогон на пять минут после успешного.
        leases.findById(run.getJob()).ifPresent(lease -> {
            lease.setLeasedUntil(now);
            leases.save(lease);
        });
    }

    /**
     * Что тронул прогон.
     *
     * @param kind    вид объекта: комната, запись, токен
     * @param id      идентификатор — строка, потому что за одним столбцом стоят
     *                четыре сущности с тремя разными типами ключа
     * @param outcome убран, оставлен или не поддался
     * @param reason  почему прогон решил именно так
     */
    public record Target(String kind, String id, String outcome, String reason) {
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String clean = value.trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
