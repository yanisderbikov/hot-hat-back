package ru.hothat.lobby.domain;

/**
 * Кому какая комната видна в витрине и кого считать живым игроком.
 *
 * <p>Правило перенесено из браузера: сегодня подписка привозит все комнаты
 * живых фаз, а отбор делает {@code home/home.js:98} у себя. Это значит, что
 * приватная и чужая тестовая комнаты приезжают в браузер каждому посетителю
 * главной вместе со всем своим содержимым, и «не показывать» их — вопрос
 * доброй воли клиента. Здесь тот же отбор делает сервер, и невидимая комната
 * не покидает сервера вовсе.
 *
 * <p>Класс без Spring и без базы: он принимает готовые признаки, а не строку
 * комнаты. Тогда правило нельзя случайно «улучшить» походом в базу, а
 * проверить его можно без поднятого приложения.
 */
public final class LobbyVisibility {

    /**
     * Сколько времени игрок считается живым после последнего появления.
     *
     * <p>Четыре минуты — то же окно, с которым отбирал состав браузер
     * ({@code home/home.js:88}: {@code Date.now() - 240000}). Порог держится
     * здесь, а не в двух местах: списку и превью нужен один и тот же ответ на
     * вопрос «этот игрок ещё в комнате».
     */
    public static final long PLAYER_ALIVE_WINDOW_MS = 240_000;

    /**
     * Сколько времени доверяем счётчику игроков, посчитанному комнатой.
     *
     * <p>Полторы минуты — то же окно, что у клиента
     * ({@code currentRoomPlayerCount}). Свежий счётчик комната обновляет сама
     * при отметке присутствия, и он стоит ноль чтений; протух — считаем по
     * строкам игроков. Убрать эту ветку и всегда считать по строкам нельзя:
     * тогда витрина из полусотни комнат тянула бы состав каждой.
     */
    public static final long PUBLIC_COUNT_FRESH_MS = 90_000;

    private LobbyVisibility() {
    }

    /**
     * Признаки комнаты, от которых зависит её видимость.
     *
     * <p>Отдельная запись, а не шесть аргументов подряд: шесть булевых
     * значений в вызове перепутать местами нельзя заметить, а по имени поля —
     * можно.
     */
    public record RoomFacts(boolean closed,
                            boolean privateRoom,
                            boolean managedMatchmaking,
                            boolean matchmakingReady,
                            boolean testRoom,
                            String testOwnerUid) {
    }

    /**
     * Видна ли комната этому зрителю.
     *
     * <p>Четыре запрета, каждый со своей причиной:
     * закрытую показывать некуда; приватную приглашают по ссылке, и в витрине
     * её быть не должно; комната, которую собирает подбор, показывается только
     * когда состав уже собран — иначе игрок увидел бы полупустую служебную
     * комнату и полез бы в неё руками; тестовую комнату видит только её
     * хозяин, потому что там сидят боты и включены владельческие поблажки.
     */
    public static boolean visibleTo(RoomFacts facts, String viewerUid) {
        if (facts.closed()) {
            return false;
        }
        if (facts.managedMatchmaking() && !facts.matchmakingReady()) {
            return false;
        }
        if (facts.testRoom()) {
            return viewerUid != null && viewerUid.equals(facts.testOwnerUid());
        }
        return !facts.privateRoom();
    }

    /** Живым игрок считается по последнему появлению; тест-бот — всегда. */
    public static boolean playerAlive(boolean testBot, long lastSeenAtMs, long nowMs) {
        return testBot || lastSeenAtMs >= nowMs - PLAYER_ALIVE_WINDOW_MS;
    }

    /** Можно ли верить счётчику, который комната посчитала сама. */
    public static boolean publicCountFresh(long publicPresenceAtMs, long nowMs) {
        return publicPresenceAtMs > 0 && nowMs - publicPresenceAtMs < PUBLIC_COUNT_FRESH_MS;
    }
}
