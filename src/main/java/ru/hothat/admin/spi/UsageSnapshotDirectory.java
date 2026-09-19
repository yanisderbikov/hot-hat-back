package ru.hothat.admin.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.admin.domain.SnapshotAuthor;
import ru.hothat.admin.usecase.UsageSnapshotCollector;

import java.time.Instant;

/** Реализация {@link UsageSnapshotPort}: живёт у владельца таблиц расхода. */
@Component
@RequiredArgsConstructor
public class UsageSnapshotDirectory implements UsageSnapshotPort {

    private final UsageSnapshotCollector collector;

    @Override
    public boolean scheduledWindowAllowed() {
        return collector.scheduledWindowAllowed();
    }

    @Override
    public Taken takeScheduled() {
        // Автор снимка — агент, а не расписание: расписание в этом продукте
        // никого не будит само, будит агент, и в истории должно стоять то, что
        // было на самом деле.
        UsageSnapshotCollector.Taken taken = collector.take(SnapshotAuthor.AGENT, null, true);
        return new Taken(
                Instant.ofEpochMilli(taken.snapshot().takenAtMs()),
                taken.alertsRaised(),
                taken.reportDelivered());
    }
}
