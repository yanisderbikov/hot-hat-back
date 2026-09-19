package ru.hothat.profile.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Присутствие в таблице {@code v2.player_presence}.
 *
 * <p>Самая горячая таблица кластера: отметка идёт раз в 30–60 секунд от
 * каждого игрока. Поэтому у таблицы {@code fillfactor 70} (обновление на
 * месте, HOT-update), а отметка объявлена одиночным {@code UPDATE}, а не
 * чтением сущности с последующим сохранением.
 */
@Repository
interface PlayerPresences extends JpaRepository<PlayerPresence, UUID> {

    List<PlayerPresence> findByPlayerIdIn(Collection<UUID> playerIds);

    /** Онлайн — те, кто отмечался за последние ~2 минуты; считает база. */
    long countByLastSeenAtGreaterThanEqual(Instant since);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PlayerPresence p set p.lastSeenAt = :now where p.playerId = :playerId")
    int touch(@Param("playerId") UUID playerId, @Param("now") Instant now);

    /**
     * Метка комнаты и время её постановки идут одним оператором: «игрок в
     * комнате, но когда попал — неизвестно» база не примет, этого требует
     * ограничение {@code ck_player_presence_room_at}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PlayerPresence p
               set p.activeRoomId = :roomId, p.activeRoomAt = :at, p.lastSeenAt = :now
             where p.playerId = :playerId
            """)
    int setActiveRoom(@Param("playerId") UUID playerId,
                      @Param("roomId") String roomId,
                      @Param("at") Instant at,
                      @Param("now") Instant now);
}
