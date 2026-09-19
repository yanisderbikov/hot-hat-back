package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.admin.api.dto.DailyActivityView;
import ru.hothat.admin.api.dto.DateRangeView;
import ru.hothat.admin.api.dto.PeriodTotalsView;
import ru.hothat.admin.api.dto.UsageStatisticsResponseDTO;
import ru.hothat.admin.domain.DateRange;
import ru.hothat.app.spi.ProductEventPort.ProductEventRow;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Считает статистику периода по просмотренным событиям.
 *
 * <p>Отдельный класс, а не тело сценария: сценарию принадлежат право,
 * транзакция и порядок шагов, а здесь — арифметика над готовым списком.
 * Считается всё за один проход по событиям, потому что второй проход означал
 * бы второе чтение тех же десяти тысяч строк.
 *
 * <p>Событие приезжает готовой записью из области {@code app}: и игрок, и
 * комната, и три счётчика партии лежат в ней колонками. Разбора json больше
 * нет — раньше он делался на каждое из десяти тысяч просмотренных событий
 * только затем, чтобы достать три числа.
 */
@Component
@RequiredArgsConstructor
public class UsageStatisticsAssembler {

    /** Типы событий, которые дашборд показывает счётчиками. */
    private static final List<String> COUNTED_TYPES = List.of(
            "room_created", "room_joined", "game_started", "game_finished",
            "account_registered", "account_login");

    private static final String GAME_FINISHED = "game_finished";

    public UsageStatisticsResponseDTO assemble(DateRange range,
                                               List<ProductEventRow> events,
                                               int scanLimit,
                                               long registeredInPeriod) {
        Set<String> uniqueUsers = new LinkedHashSet<>();
        Set<String> uniqueRooms = new LinkedHashSet<>();
        Map<String, Integer> counters = new LinkedHashMap<>();
        COUNTED_TYPES.forEach(type -> counters.put(type, 0));
        Map<String, DailyRow> daily = new LinkedHashMap<>();

        long finishedPlayers = 0;
        long finishedTeams = 0;
        long finishedWords = 0;
        int finishedSamples = 0;

        for (ProductEventRow event : events) {
            if (event.uid() != null) {
                uniqueUsers.add(event.uid());
            }
            if (event.roomId() != null) {
                uniqueRooms.add(event.roomId());
            }
            counters.computeIfPresent(event.eventType(), (key, value) -> value + 1);
            if (GAME_FINISHED.equals(event.eventType())) {
                finishedPlayers += event.playerCount();
                finishedTeams += event.teamCount();
                finishedWords += event.wordCount();
                finishedSamples++;
            }
            if (event.occurredAt() == null) {
                continue;
            }
            String day = event.occurredAt().atZone(ZoneOffset.UTC).toLocalDate().toString();
            DailyRow row = daily.computeIfAbsent(day, DailyRow::new);
            if (event.uid() != null) {
                row.users.add(event.uid());
            }
            if (event.roomId() != null) {
                row.rooms.add(event.roomId());
            }
            if (GAME_FINISHED.equals(event.eventType())) {
                row.games++;
            }
        }

        PeriodTotalsView period = new PeriodTotalsView(
                uniqueUsers.size(),
                uniqueRooms.size(),
                counters.get("room_created"),
                counters.get("room_joined"),
                counters.get("game_started"),
                counters.get(GAME_FINISHED),
                // Событие регистрации могло не записаться, а учётка есть:
                // берём большее из двух счётчиков, как и прежний движок.
                Math.max(counters.get("account_registered"), registeredInPeriod),
                counters.get("account_login"),
                average(finishedPlayers, finishedSamples),
                average(finishedTeams, finishedSamples),
                average(finishedWords, finishedSamples),
                events.size(),
                events.size() >= scanLimit);

        List<DailyActivityView> rows = new ArrayList<>();
        for (DailyRow row : daily.values()) {
            rows.add(new DailyActivityView(row.day, row.users.size(), row.rooms.size(), row.games));
        }

        DateRangeView rangeView = new DateRangeView(
                range.startDay(), range.endDay(),
                range.startAt().toEpochMilli(), range.endAt().toEpochMilli());
        return new UsageStatisticsResponseDTO(rangeView, period, rows);
    }

    /** Одна десятая — та же точность, с какой средние показывались всегда. */
    private static double average(long sum, int samples) {
        return samples == 0 ? 0 : Math.round((double) sum / samples * 10) / 10.0;
    }

    /** Накопитель одного дня: множества нужны, чтобы считать разных, а не всех. */
    private static final class DailyRow {

        private final String day;
        private final Set<String> users = new LinkedHashSet<>();
        private final Set<String> rooms = new LinkedHashSet<>();
        private int games;

        private DailyRow(String day) {
            this.day = day;
        }
    }
}
