package ru.hothat.chat.store;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Дверь в {@code v2.direct_chat_message}; за пределы области не выходит. */
@Repository
interface DirectMessages extends JpaRepository<DirectMessage, Long> {

    /**
     * История листается назад от свежего — по идентификатору, а не по времени.
     * Идентификатор выдаёт база и он строго растёт, а отметки времени двух
     * сообщений могут совпасть до миллисекунды.
     */
    List<DirectMessage> findByChatIdOrderByIdDesc(Long chatId, Limit limit);

    /**
     * Последнее входящее во всех переписках игрока — для всплывающего
     * уведомления. Один запрос: подзапрос по своим строкам участия идёт по
     * индексу {@code ix_direct_chat_member_player}.
     */
    @Query("select m from DirectMessage m where m.senderPlayerId <> :playerId "
            + "and m.chatId in (select b.chatId from DirectChatMembership b where b.playerId = :playerId) "
            + "order by m.id desc")
    List<DirectMessage> findLastIncoming(@Param("playerId") UUID playerId, Limit limit);
}
