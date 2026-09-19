package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.admin.api.dto.UsageStatisticsQueryDTO;
import ru.hothat.admin.api.dto.UsageStatisticsResponseDTO;
import ru.hothat.admin.domain.DateRange;
import ru.hothat.config.HotHatUser;
import ru.hothat.app.spi.ProductEventPort;
import ru.hothat.auth.spi.AccountPort;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Показать администратору статистику за период.
 *
 * <p>Заменяет {@code GET /api/admin?scope=stats}. Разбор диапазона уехал в
 * {@link DateRange} — двадцать восемь строк из контроллера (F13); здесь
 * остались право, порядок шагов и два чтения.
 *
 * <p>Чтений ровно два, и оба пакетные: события за период одним запросом с
 * пределом просмотра и счётчик регистраций одним {@code count}. Событий
 * больше предела быть не может — в ответе для этого есть {@code truncated},
 * чтобы администратор видел, что числа занижены, а не гадал.
 */
@Service
@RequiredArgsConstructor
public class GetUsageStatisticsUseCase {

    /**
     * Сколько событий просматриваем за раз. Потолок не про здравый смысл, а
     * про память: события приезжают в кучу целиком, и без предела запрос за
     * год положил бы процесс.
     */
    private static final int EVENT_SCAN_LIMIT = 10000;

    /** Журнал продуктовых событий принадлежит области app; здесь его читают. */
    private final ProductEventPort events;
    /** Сколько человек зарегистрировалось — знает область личности. */
    private final AccountPort accounts;
    private final UsageStatisticsAssembler assembler;

    /** См. пояснение к такому же полю в {@code GetLiveRoomDashboardUseCase}. */
    private final Clock clock = Clock.systemUTC();

    @PreAuthorize("hasRole('ADMIN')")
    public UsageStatisticsResponseDTO run(HotHatUser admin, UsageStatisticsQueryDTO query) {
        LocalDate start = query == null ? null : query.start();
        LocalDate end = query == null ? null : query.end();
        DateRange range = DateRange.lastWeekByDefault(start, end, clock);
        return assembler.assemble(
                range,
                events.between(range.startAt(), range.endAt(), EVENT_SCAN_LIMIT),
                EVENT_SCAN_LIMIT,
                accounts.countRegisteredBetween(range.startAt(), range.endAt()));
    }
}
