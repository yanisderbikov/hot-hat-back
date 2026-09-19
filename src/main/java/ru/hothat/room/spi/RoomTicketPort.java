package ru.hothat.room.spi;

import java.util.List;
import java.util.Optional;

/**
 * Комната подбора глазами лобби.
 *
 * <p>Автокомната — обычная комната: у неё та же строка, те же места и те же
 * команды. Заводит, наполняет и закрывает её область комнаты, поэтому лобби
 * получает порт, а не {@code GetterRoom}. Иначе у строки комнаты оказалось бы
 * два писателя из разных областей — то самое, ради развязки чего затевался
 * переезд.
 *
 * <p>Разделение обязанностей такое: <b>кого с кем сводить</b> решает лобби —
 * язык, режим, размер, разброс рейтинга, срок поиска. <b>Как это ложится в
 * комнату</b> — здесь: слоты команд, места игроков, признак готовности.
 *
 * <p>Все методы записи возвращают комнату после изменения, а не {@code void}:
 * заявка отвечает подавшему числом собравшихся и сроком, и второе чтение ради
 * этого было бы чтением того, что вызывающий только что записал.
 */
public interface RoomTicketPort {

    /**
     * Открытые автокомнаты подбора — те, что ещё набираются.
     *
     * <p>Одна выборка на весь отбор. Прежний движок читал восемьдесят комнат
     * фазы {@code setup} на каждый опрос каждого ищущего игрока; предел
     * остался, но читает его теперь один сценарий подачи заявки, а не опрос
     * раз в три секунды.
     */
    List<TicketRoom> openTicketRooms(int limit);

    /** Одна комната подбора; пусто — комнаты нет вовсе. */
    Optional<TicketRoom> ticketRoom(String roomId);

    /** Завести автокомнату под заявку. */
    TicketRoom openManagedRoom(NewManagedRoom spec);

    /** Дописать одиночного игрока в заявку существующей комнаты. */
    TicketRoom addApplicant(String roomId, Applicant applicant);

    /**
     * Посадить рейтинговую пару в свободный слот команды.
     *
     * <p>Пара занимает слот целиком: напарников не разлучают ни при входе, ни
     * при выходе. Свободных слотов нет — отказ {@code ROOM_FULL} 409.
     */
    TicketRoom seatPair(String roomId, RankedPair pair);

    /** Отметить, что состав собран. */
    TicketRoom markReady(String roomId);

    /** Закрыть комнату, не собравшуюся в срок. */
    void expire(String roomId);

    /**
     * Снять заявку названных игроков; опустевшую комнату закрыть.
     *
     * @param rankedTeamId слот рейтинговой пары, который освобождается вместе
     *                     с ней; {@code null} — заявка одиночная
     */
    void withdraw(String roomId, List<String> uids, String rankedTeamId);

    /**
     * Комната подбора в объёме, нужном лобби.
     *
     * @param applicantUids кто уже стоит в этой заявке
     * @param rankedTeamIds какие пары уже заняли слоты
     * @param ratingTarget  вес комнаты при сведении пар; 1000 у комнаты без веса
     */
    record TicketRoom(String roomId,
                      String hostUid,
                      boolean ranked,
                      boolean privateRoom,
                      boolean managed,
                      boolean closed,
                      String phase,
                      String gameMode,
                      int maxPlayers,
                      String divisionLanguage,
                      String matchmakingLanguage,
                      List<String> applicantUids,
                      List<String> rankedTeamIds,
                      long deadlineMs,
                      int ratingTarget,
                      boolean ready,
                      boolean expired) {
    }

    /** Одиночный подавший: имя нужно, чтобы соседи по заявке видели, с кем ждут. */
    record Applicant(String uid, String nickname) {
    }

    /**
     * Участник рейтинговой пары.
     *
     * @param loadout обойма мемов: место игрока держит свою копию, и завести
     *                его неполным значило бы упереться в отказ на старте партии
     */
    record PairMember(String uid, String nickname, String avatarDataUrl, List<String> loadout) {
    }

    /** Рейтинговая пара целиком: слот команды заводится под неё. */
    record RankedPair(String rankedTeamId, String name, List<PairMember> members) {
    }

    /**
     * Заказ на автокомнату.
     *
     * @param applicant одиночный подавший; {@code null} у рейтинговой заявки
     * @param pair      пара; {@code null} у быстрой заявки
     */
    record NewManagedRoom(String hostUid,
                          boolean ranked,
                          String gameMode,
                          int maxPlayers,
                          String divisionLanguage,
                          String matchmakingLanguage,
                          Integer ratingTarget,
                          long deadlineMs,
                          Applicant applicant,
                          RankedPair pair) {
    }
}
