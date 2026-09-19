package ru.hothat.chat.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Шапка личной переписки: пара и указатель на последнее сообщение.
 *
 * <p>Ключ — суррогат, а не склейка uid: прежний {@code pair} VARCHAR(340)
 * ездил внешним ключом в каждое сообщение, и сорок лишних байт на строку
 * оплачивались только тем, что ключ читается глазами.
 *
 * <p>{@link #lastMessageId} и {@link #lastMessageAt} выводимы из сообщений и
 * держатся здесь ради одного запроса: список переписок сортируется по свежести
 * и показывает превью. Без указателя это «последнее в каждой группе» — тот
 * самый скоррелированный подзапрос, который сегодня написан руками, потому что
 * именами метода он не выражается. Текст превью и автор при этом не
 * копируются: их отдаёт строка сообщения, на которую указывает
 * {@link #lastMessageId}.
 *
 * <p>Класс не публичный: сущность не покидает {@code chat.store}.
 */
@Entity
@Table(name = "direct_chat", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class DirectChatThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "player_low", nullable = false, updatable = false)
    private UUID playerLow;

    @Column(name = "player_high", nullable = false, updatable = false)
    private UUID playerHigh;

    @Column(name = "last_message_id")
    private Long lastMessageId;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
