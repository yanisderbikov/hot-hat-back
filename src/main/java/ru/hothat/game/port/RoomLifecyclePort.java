package ru.hothat.game.port;

import java.util.List;
import java.util.Optional;

/**
 * Что область партии просит у области комнаты.
 *
 * <p>Комната и партия — разные вещи, и владелец у них разный: места, хозяин,
 * зрители и вместимость принадлежат комнате, а ходы, шляпа и счёт — партии.
 * Партия не пишет в чужие таблицы и не читает чужие репозитории: она просит
 * через этот порт, а исполняет просьбу владелец, в той же транзакции.
 *
 * <p>Порт объявлен здесь, у потребителя, намеренно: список требований должен
 * читаться там, где им пользуются. Реализация принадлежит области комнаты;
 * пока она пишется, её место занимает временный переходник над сегодняшними
 * репозиториями, и заменить его — значит подменить один бин.
 */
public interface RoomLifecyclePort {

    /** Комната партии; пусто — комнаты больше нет. */
    Optional<RoomLifecycleView> find(String roomId);

    /** Места комнаты: по ним собирается состав партии на старте. */
    List<RoomSeatView> seats(String roomId);

    Optional<RoomSeatView> seat(String roomId, String uid);

    boolean isHost(String roomId, String uid);

    boolean isMember(String roomId, String uid);

    boolean isSpectator(String roomId, String uid);

    /**
     * Партия началась: комната отмечает это у себя.
     *
     * <p>Зрительские места игроков освобождаются здесь же: место зрителя и
     * место игрока — одно и то же кресло, и остаться в обоих нельзя.
     */
    void onMatchStarted(String roomId, int gameNumber, List<String> playerUids);

    /** Партия закончилась — обычным путём либо технически. */
    void onMatchFinished(String roomId, int gameNumber, String reason);

    /** Комната закрывается: доигрывать некому и незачем. */
    void closeRoom(String roomId, String reason);

    /** Отметка присутствия игрока в комнате. */
    void touchSeat(String roomId, String uid, long atMs);

    /**
     * Игрок ушёл: отметка присутствия и признаки связи гасятся немедленно.
     *
     * <p>До опроса LiveKit, а не после: его список участников отдаёт ушедшего
     * ещё несколько секунд, и запоздалая проверка сняла бы паузу зря.
     */
    void dropSeatPresence(String roomId, String uid);

    /**
     * Комната глазами партии.
     *
     * @param turnDurationSeconds длительность хода по настройке комнаты
     * @param recordGame          партия пишется в видеофайл
     */
    record RoomLifecycleView(String roomId,
                             String name,
                             String hostUid,
                             String phase,
                             int gameNumber,
                             String gameMode,
                             boolean ranked,
                             boolean testRoom,
                             boolean recordGame,
                             String divisionLanguage,
                             String gameLanguage,
                             int maxPlayers,
                             double turnDurationSeconds,
                             List<String> testBotIds) {
    }

    /**
     * Место в комнате.
     *
     * @param lastSeenAt последняя отметка присутствия; у тест-бота её нет вовсе
     */
    record RoomSeatView(String uid, String name, String teamId, boolean testBot, long lastSeenAt) {

        /** Окно активности: пять минут без отметки — и участник уже не в комнате. */
        public static final long ACTIVE_WINDOW_MS = 5 * 60 * 1000;

        public boolean active(long nowMs) {
            return testBot || lastSeenAt >= nowMs - ACTIVE_WINDOW_MS;
        }
    }
}
