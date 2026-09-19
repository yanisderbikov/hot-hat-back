package ru.hothat.chat.store;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Дверь в {@code v2.direct_chat_member} — таблицу, ради которой затевалась
 * развязка переписки: непрочитанное теперь своё у каждого участника.
 */
@Repository
interface DirectChatMemberships extends JpaRepository<DirectChatMembership, DirectChatMembershipId> {

    /** «Мои переписки» — одна выборка по своим строкам. */
    List<DirectChatMembership> findByPlayerIdOrderByChatIdDesc(UUID playerId, Limit limit);

    @Modifying
    @Query(value = "insert into v2.direct_chat_member (chat_id, player_id, unread_count, updated_at) "
            + "values (:chatId, :playerId, 0, now()) on conflict do nothing", nativeQuery = true)
    void insertIfAbsent(@Param("chatId") Long chatId, @Param("playerId") UUID playerId);

    /**
     * Плюс одно непрочитанное — прибавлением в базе, а не чтением в память.
     *
     * <p>Это и есть развязка: раньше счётчики обоих собеседников лежали одним
     * jsonb в шапке, читались целиком и записывались целиком, и «отправил»
     * одного затирал «прочитал» другого. Здесь прибавление атомарно, и
     * значение в память не поднимается вовсе.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DirectChatMembership m set m.unreadCount = m.unreadCount + 1, m.updatedAt = :now "
            + "where m.chatId = :chatId and m.playerId = :playerId")
    void bumpUnread(@Param("chatId") Long chatId, @Param("playerId") UUID playerId,
                    @Param("now") Instant now);

    /**
     * Убрать из переписки обоих участников: пара перестала быть друзьями.
     *
     * <p>Удаляются только строки участия. Шапка и сообщения остаются: история
     * переживает разрыв, и если те же двое подружатся снова, {@code openThread}
     * найдёт ту же переписку и заведёт участие заново — со всем написанным.
     *
     * <p>Одним оператором, а не выборкой с последующим удалением: строк ровно
     * две, и поднимать их в память незачем.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DirectChatMembership m where m.chatId = :chatId")
    void deleteMembers(@Param("chatId") Long chatId);

    /** Значок непрочитанного в шапке портала: одна сумма по своим строкам. */
    @Query("select coalesce(sum(m.unreadCount), 0) from DirectChatMembership m where m.playerId = :playerId")
    long totalUnread(@Param("playerId") UUID playerId);
}
