package ru.hothat.testbot.port;

import ru.hothat.config.HotHatUser;

import java.util.List;

/**
 * Движок отряда тестовых ботов глазами сценариев области.
 *
 * <p>Порт объявлен здесь, а реализация — переходник поверх ещё не переехавшего
 * {@code service/testbots}: боты рассаживаются по местам комнаты и ведут ход
 * партии, то есть живут в двух чужих кластерах сразу, и переехать раньше них не
 * могут. Что мы получаем уже сейчас: сценарии не знают ни одного легаси-имени,
 * ответы типизированы, а разбор карт заперт в одном классе. Когда движок
 * переедет, меняется только этот класс — адрес, DTO и права остаются.
 *
 * <p>{@link HotHatUser} в подписи, а не голый uid: движок ведёт ход от имени
 * владельца комнаты и передаёт его дальше — закрытие апелляции требует
 * настоящего вызывающего, а не строки.
 */
public interface TestBotEnginePort {

    /** Поднять отряд в тестовой комнате. */
    Squad setUp(HotHatUser owner, String roomId);

    /** Распустить отряд и закрыть комнату. Уже закрытая — не ошибка. */
    void stop(HotHatUser owner, String roomId);

    /** Один шаг раннера: ход, выстрелы, чат. */
    Turn advance(HotHatUser owner, String roomId);

    /**
     * Ручная диверсия владельца — без боезапаса и кулдауна.
     *
     * @param eventId идентификатор от клиента; повторный запрос с тем же
     *                значением не заводит второго события
     */
    Shot fireOwnerFart(HotHatUser owner, String roomId, String eventId);

    /** Состав, поднятый в комнате. */
    record Squad(String roomId, List<String> botIds, List<String> teamOrder, int maxPlayers) {
    }

    /**
     * Итог шага.
     *
     * <p>{@code action} — сырое слово движка: перевод его в закрытый набор —
     * дело сценария, а порту незачем знать про представление ответа.
     * {@code botShots} и {@code botChat} пусты, когда шаг был не боевой.
     */
    record Turn(String phase, String action, long waitMs, Integer botShots, Boolean botChat) {
    }

    /** Событие диверсии так, как его получают участники комнаты. */
    record Shot(String id, String type, String attackerUid, String attackerName,
                String targetUid, long createdAtMs, long durationMs, int gameNumber) {
    }
}
