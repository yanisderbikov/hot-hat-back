package ru.hothat.chat.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Дверь в {@code v2.direct_chat}; за пределы области переписки не выходит. */
@Repository
interface DirectChatThreads extends JpaRepository<DirectChatThread, Long> {

    Optional<DirectChatThread> findByPlayerLowAndPlayerHigh(UUID playerLow, UUID playerHigh);

    /**
     * Завести шапку, если её ещё нет.
     *
     * <p>Вставка с {@code on conflict do nothing} вместо «прочитал — не нашёл —
     * вставил»: два первых сообщения одной пары, отправленных одновременно,
     * на проверке перед вставкой расходились в две шапки, а уникальный ключ
     * пары превращал вторую в ошибку 500. Здесь гонку гасит сама база, а
     * вызывающий просто перечитывает строку.
     */
    @Modifying
    @Query(value = "insert into v2.direct_chat (player_low, player_high) values (:playerLow, :playerHigh) "
            + "on conflict do nothing", nativeQuery = true)
    void insertIfAbsent(@Param("playerLow") UUID playerLow, @Param("playerHigh") UUID playerHigh);

    /**
     * Указатель на последнее сообщение. Отдельным запросом, а не через
     * сущность: шапку читают оба собеседника, и загружать её в память ради
     * двух колонок незачем.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DirectChatThread t set t.lastMessageId = :messageId, t.lastMessageAt = :at "
            + "where t.chatId = :chatId")
    void pointAtLastMessage(@Param("chatId") Long chatId, @Param("messageId") Long messageId,
                            @Param("at") Instant at);
}
