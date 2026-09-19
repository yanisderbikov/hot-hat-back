package ru.hothat.app.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.app.store.AnalyticsStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Реализация {@link ProductEventPort}: живёт у владельца таблицы. */
@Component
@RequiredArgsConstructor
public class ProductEventDirectory implements ProductEventPort {

    private final AnalyticsStore analytics;

    @Override
    @Transactional(readOnly = true)
    public List<ProductEventRow> between(Instant from, Instant to, int limit) {
        List<AnalyticsStore.Recorded> rows = analytics.between(from, to, limit);
        List<ProductEventRow> result = new ArrayList<>(rows.size());
        for (AnalyticsStore.Recorded row : rows) {
            result.add(new ProductEventRow(
                    row.eventType(), row.uid(), row.roomId(),
                    row.playerCount(), row.teamCount(), row.wordCount(), row.occurredAt()));
        }
        return result;
    }
}
