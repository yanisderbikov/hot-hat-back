package ru.hothat.room.port;

import java.util.List;
import java.util.Optional;

/**
 * Что область комнаты требует от видеосвязи.
 *
 * <p>Порт, а не прямой вызов клиента LiveKit: сценарий выдачи токена решает,
 * <b>кого</b> пускать и <b>с какими правами</b>, а как из этого получается
 * подписанный JWT и учётка ретранслятора — предмет внешней системы. Ровно так
 * же устроено присутствие в партии ({@code game.port.MatchPresencePort}), и
 * реализация здесь тоже лежит в {@code store} — единственном слое, которому
 * позволено знать чужие протоколы.
 *
 * <p>Права участника выражены двумя признаками вместо девяти флагов SDK:
 * комната различает всего два случая — тот, кто показывает себя, и тот, кто
 * только смотрит. Остальные семь флагов у обоих одинаковы, и повторять их в
 * каждом вызове значило бы дать возможность разойтись.
 */
public interface VideoSessionPort {

    /** Адрес сервера видеосвязи, который называют браузеру. */
    String serverUrl();

    /** Подписанный токен участника. */
    String participantToken(Participant participant);

    /**
     * Короткоживущая учётка ретранслятора для этого участника.
     *
     * <p>Пусто — ретранслятор не настроен вовсе. Это не ошибка: без него связь
     * держится напрямую, и отвечать отказом значило бы выключить видео там,
     * где оно и так работает.
     */
    Optional<TurnTicket> turnTicket(String identity);

    /**
     * Участник, за которого просят токен.
     *
     * @param publisher может показывать себя; зритель — нет
     * @param teamId    команда участника; {@code null} у зрителя
     * @param role      {@code player} либо {@code spectator} — едет в метаданные
     *                  и по нему клиенты различают места в комнате
     */
    record Participant(String roomId,
                       String identity,
                       String name,
                       boolean publisher,
                       String teamId,
                       String role,
                       boolean testBot,
                       int gameNumber) {
    }

    /**
     * Учётка ретранслятора: адреса, имя, пароль и срок.
     *
     * @param expiresAtSeconds момент, до которого учётка жива, — тот самый,
     *                         что стоит началом {@code username}. Считается
     *                         один раз: второй расчёт разошёлся бы с подписью
     */
    record TurnTicket(List<String> urls, String username, String credential,
                      long ttlSeconds, long expiresAtSeconds) {
    }
}
