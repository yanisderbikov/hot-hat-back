package ru.hothat.chat.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Непрочитанное и прогресс чтения — на участника, а не на переписку.
 *
 * <p>Сегодня это три jsonb в шапке переписки: {@code unread_counts},
 * {@code unread_for} и {@code last_read_at_ms}, каждый с uid в ключе. Оба
 * участника пишут один и тот же документ, читая его целиком, — и «отправил»
 * одного затирает «прочитал» другого. Строка на участника делает счётчик
 * атомарным: {@code UPDATE … SET unread_count = unread_count + 1} не читает
 * значение в память вовсе, поэтому и {@code @Version} здесь не нужен —
 * побеждает последняя запись, и это верная семантика.
 *
 * <p>{@link #lastReadMessageId} — водяной знак без внешнего ключа: он обязан
 * пережить удаление сообщения, на которое показывает, иначе одно удаление
 * обнулило бы прогресс чтения.
 */
@Entity
@Table(name = "direct_chat_member", schema = "v2")
@IdClass(DirectChatMembershipId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class DirectChatMembership {

    @Id
    @Column(name = "chat_id", nullable = false, updatable = false)
    private Long chatId;

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    @Builder.Default
    @Column(name = "unread_count", nullable = false)
    private Integer unreadCount = 0;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
