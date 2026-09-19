package ru.hothat.game.store;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.model.room.RoomTeamId;

import java.time.Instant;

/**
 * Прибавка к счёту команды — одним действием базы.
 *
 * <p>Так, а не «прочитать, прибавить, записать»: два угаданных слова в разных
 * командах приходят одновременно, и вторая запись затёрла бы первую. Это
 * ровно то потерянное обновление, которое аудит нашёл в клиентских
 * транзакциях (B2); клиент обходил его атомарным {@code increment()}, и
 * серверу нужен его точный аналог.
 */
@org.springframework.stereotype.Repository
public interface MatchTeamScoreRepo extends Repository<RoomTeam, RoomTeamId> {

    /**
     * {@code flushAutomatically} — чтобы прибавка легла после уже накопленных
     * изменений партии, {@code clearAutomatically} — чтобы ответ, собираемый
     * следом, прочитал новый счёт: обходя контекст сохранения, запрос оставил
     * бы в нём прежнюю строку команды, и клиент увидел бы счёт до прибавки.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RoomTeam t set t.score = t.score + :delta, t.updatedAt = :now "
            + "where t.roomId = :roomId and t.teamId = :teamId")
    int addScore(@Param("roomId") String roomId,
                 @Param("teamId") String teamId,
                 @Param("delta") int delta,
                 @Param("now") Instant now);
}
