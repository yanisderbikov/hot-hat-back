package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.admin.api.dto.UsageAlertView;
import ru.hothat.admin.api.dto.UsageSnapshotDayView;
import ru.hothat.admin.api.dto.UsageSnapshotPageResponseDTO;
import ru.hothat.admin.api.dto.UsageSnapshotQueryDTO;
import ru.hothat.admin.api.dto.UsageSnapshotView;
import ru.hothat.admin.domain.DateRange;
import ru.hothat.admin.store.UsageStore;
import ru.hothat.config.HotHatUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Показать администратору историю снимков расхода.
 *
 * <p>Заменяет {@code GET /api/monitor} — маршрут в {@code permitAll}, который
 * проверял администратора строкой внутри метода (A12). Право теперь стоит
 * здесь, а маршрут накрыт матчером поддерева.
 *
 * <p>Трёх ключей прежнего ответа больше нет. {@code selected} и
 * {@code selectedDate} были второй формой ответа внутри той же операции:
 * клиент просил один день, посылая одинаковые концы диапазона, и получал его
 * отдельным полем вдобавок к списку. Теперь один день — это страница из одного
 * элемента. {@code selectedNoData} выражался наличием элемента в списке.
 *
 * <p>Строка истории — это снимок, а не день. Их снимают четыре раза в сутки, и
 * пока снимок был строкой с ключом-датой, три из четырёх затирали друг друга;
 * теперь в один день их приезжает столько, сколько сняли, свежие сверху. Поле
 * {@code day} осталось тем же и по-прежнему называет день снимка.
 *
 * <p>Чтений три, все пакетные: страница снимков, самый свежий снимок и
 * последние предупреждения. Отдельное чтение свежего нужно потому, что сводка
 * «здоровье систем» обязана оставаться текущей, пока человек листает историю.
 */
@Service
@RequiredArgsConstructor
public class ListUsageSnapshotsUseCase {

    /** Столько предупреждений показывает панель; больше в неё не помещается. */
    private static final int ALERT_LIMIT = 50;

    private final UsageStore usage;
    private final UsageSnapshotMapper mapper;

    @PreAuthorize("hasRole('ADMIN')")
    public UsageSnapshotPageResponseDTO run(HotHatUser admin, UsageSnapshotQueryDTO query) {
        int limit = query == null ? UsageSnapshotQueryDTO.DEFAULT_LIMIT : query.limitOrDefault();
        Optional<DateRange> range = query == null
                ? Optional.empty()
                : DateRange.explicit(query.from(), query.to());

        List<UsageSnapshotDayView> items = new ArrayList<>();
        for (UsageStore.Stored stored : usage.history(
                range.map(DateRange::start).orElse(null),
                range.map(DateRange::end).orElse(null),
                limit)) {
            items.add(new UsageSnapshotDayView(stored.day().toString(), mapper.snapshot(stored)));
        }

        List<UsageAlertView> alerts = new ArrayList<>();
        for (UsageStore.Alert alert : usage.recentAlerts(ALERT_LIMIT)) {
            alerts.add(mapper.alert(alert));
        }

        UsageSnapshotView latest = usage.latest().map(mapper::snapshot).orElse(null);
        return new UsageSnapshotPageResponseDTO(items, null, limit, latest, alerts);
    }
}
