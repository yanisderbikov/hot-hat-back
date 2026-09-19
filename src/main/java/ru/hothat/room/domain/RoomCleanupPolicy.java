package ru.hothat.room.domain;

import java.util.List;

/**
 * Когда комнату можно убрать.
 *
 * <p>Переезд {@code RoomCleanupServiceImpl.emptyState}. Правило одно на два
 * повода: точечную уборку за вышедшим человеком и общий проход уборщика. Две
 * формулы «комната опустела» разошлись бы на первой же правке порогов, а
 * разница между ними видна не сразу — комната остаётся висеть в витрине на
 * главной до десяти минут.
 *
 * <p>Тест-боты в счёт не идут: они намеренно не шлют отметок присутствия, и по
 * общему правилу брошенный тестовый прогон подвешивал бы комнату навсегда.
 * Поэтому сюда приезжают отметки только живых людей.
 *
 * <p>Класс без Spring и без базы: принимает готовые признаки, а не строку
 * комнаты. Время — аргументом, а не {@code System.currentTimeMillis()}.
 */
public final class RoomCleanupPolicy {

    /** Чаще этого общий проход не запускается: он читает тысячу комнат. */
    public static final long SWEEP_COOLDOWN_MS = 5 * 60 * 1000;

    /** Столько молчания хватает, чтобы считать комнату в наборе брошенной. */
    public static final long STALE_PLAYER_MS = 10 * 60 * 1000;

    /**
     * В идущей партии запас на обрыв связи короче.
     *
     * <p>Не из строгости: комната с идущей партией держит слова, счёт и
     * составы, и оставлять её на десять минут после того, как все ушли,
     * значило бы показывать в витрине партию, в которую нельзя войти.
     */
    public static final long STALE_ACTIVE_GAME_MS = 6 * 60 * 1000;

    /**
     * Две минуты форы только что созданной комнате.
     *
     * <p>Между записью комнаты и первым heartbeat её хозяина проходит время:
     * без форы общий проход убирал бы комнаты, которые ещё открываются. На
     * точечную уборку фора не распространяется — там про комнату спросили
     * прямо, и ответ «подожди две минуты» никому не нужен.
     */
    public static final long NEW_ROOM_GRACE_MS = 2 * 60 * 1000;

    private RoomCleanupPolicy() {
    }

    /**
     * Комната глазами уборщика.
     *
     * @param createdAtMs      когда её завели; 0 — неизвестно, форы не будет
     * @param humanLastSeenAtMs отметки присутствия людей, без тест-ботов
     */
    public record Occupancy(RoomPhase phase, long createdAtMs, List<Long> humanLastSeenAtMs) {
    }

    /**
     * Приговор с причиной.
     *
     * <p>Причина есть у обоих исходов, потому что её показывает ответ
     * {@code /api/cleanup-rooms}: «оставили, потому что в ней играют» и
     * «оставили, потому что она только что создана» — разные ответы.
     */
    public record Verdict(boolean abandoned, String reason) {
    }

    /**
     * Брошена ли комната.
     *
     * @param targeted спросили про эту комнату прямо — тогда фора новой
     *                 комнате не даётся: за уходящим убирают сразу
     */
    public static Verdict verdict(Occupancy room, boolean targeted, long nowMs) {
        List<Long> lastSeen = room.humanLastSeenAtMs();
        if (lastSeen.isEmpty()) {
            boolean recent = room.createdAtMs() > 0
                    && nowMs - room.createdAtMs() < NEW_ROOM_GRACE_MS;
            return new Verdict(targeted || !recent,
                    recent && !targeted ? "new-room-grace" : "no-human-players");
        }
        if (lastSeen.stream().allMatch(value -> value <= 0)) {
            return new Verdict(true, "all-humans-left");
        }
        boolean liveGame = room.phase().isLive();
        long staleMs = liveGame ? STALE_ACTIVE_GAME_MS : STALE_PLAYER_MS;
        if (lastSeen.stream().allMatch(value -> value <= 0 || value < nowMs - staleMs)) {
            return new Verdict(true, liveGame ? "abandoned-game" : "stale-10m");
        }
        return new Verdict(false, "has-players");
    }
}
