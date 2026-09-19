package ru.hothat.app.store;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import ru.hothat.common.identity.LegacyIdBridge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Единственная дверь области {@code app} в журнал продуктовых событий.
 *
 * <p>Наружу отдаёт записи, а не сущности: {@link ProductEvent} не публичен.
 * Здесь же живёт перевод uid ↔ uuid — таблица объявляет игрока как uuid,
 * а живой идентификатор до переезда области {@code auth} остаётся строкой.
 *
 * <p>Событие пишется шестью колонками, а не одним jsonb: статистика периода
 * считает по ним средние, и разбор json на каждое из десяти тысяч
 * прочитанных событий был чистой платой за то, что поля не назвали.
 *
 * <p>Почты у события нет намеренно: она нужна отчёту, читается по игроку и
 * копией в журнале пережила бы собственную смену.
 */
@Component
@RequiredArgsConstructor
public class AnalyticsStore {

    private final ProductEvents events;
    private final LegacyIdBridge ids;

    /**
     * Записать событие. Идемпотентно по ключу: обработчик конца партии
     * срабатывает у каждого участника, и одно событие приезжает пять раз.
     *
     * @return {@code false}, если событие с таким ключом уже записано
     */
    public boolean record(String uid, Event event) {
        if (events.existsByEventKey(event.eventKey())) {
            return false;
        }
        // Игрока надо не только посчитать, но и назвать обратно в отчёте,
        // поэтому uuid запоминается мостом, а не только выводится из uid.
        ids.rememberPlayers(List.of(uid));
        events.save(ProductEvent.builder()
                .eventKey(event.eventKey())
                .eventType(event.eventType())
                .playerId(ids.playerId(uid))
                .roomId(event.roomId())
                .roomName(event.roomName())
                .playerCount(event.playerCount())
                .teamCount(event.teamCount())
                .wordCount(event.wordCount())
                .gameNumber(event.gameNumber())
                .durationSeconds(event.durationSeconds())
                .build());
        return true;
    }

    /**
     * События периода одной выборкой, не больше предела просмотра.
     *
     * <p>Игрок возвращается строковым uid: отчёт считает по нему уникальных,
     * а обратный перевод без таблицы моста невозможен, и делать его в цикле
     * по десяти тысячам строк нельзя — поэтому он пакетный.
     */
    public List<Recorded> between(Instant from, Instant to, int limit) {
        List<ProductEvent> rows = events.findByOccurredAtBetweenOrderByOccurredAtAsc(from, to, Limit.of(limit));
        List<UUID> playerIds = new ArrayList<>(rows.size());
        for (ProductEvent row : rows) {
            playerIds.add(row.getPlayerId());
        }
        var byId = ids.playerUids(playerIds);
        List<Recorded> result = new ArrayList<>(rows.size());
        for (ProductEvent row : rows) {
            result.add(new Recorded(
                    row.getEventType(),
                    byId.get(row.getPlayerId()),
                    row.getRoomId(),
                    row.getPlayerCount() == null ? 0 : row.getPlayerCount(),
                    row.getTeamCount() == null ? 0 : row.getTeamCount(),
                    row.getWordCount() == null ? 0 : row.getWordCount(),
                    row.getOccurredAt()));
        }
        return result;
    }

    /** Событие, каким его кладёт сценарий: все поля уже проверены на входе. */
    public record Event(String eventType,
                        String eventKey,
                        String roomId,
                        String roomName,
                        Short playerCount,
                        Short teamCount,
                        Integer wordCount,
                        Integer gameNumber,
                        Integer durationSeconds) {
    }

    /** Событие, каким его читает отчёт. */
    public record Recorded(String eventType,
                           String uid,
                           String roomId,
                           int playerCount,
                           int teamCount,
                           int wordCount,
                           Instant occurredAt) {
    }
}
