package ru.hothat.model.room;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import lombok.*;

import java.time.Instant;

/** rooms/{id}/chat/{messageId}: сейчас пишут только тест-боты. */
@Entity
@Table(name = "room_chat_message")
@IdClass(RoomChatMessageId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomChatMessage {

    @Id
    @Column(name = "room_id", nullable = false, length = 80)
    private String roomId;

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(length = 160)
    private String uid;

    @Column(length = 80)
    private String name;

    @Column(length = 24)
    private String role;

    @Column(length = 800)
    private String text;

    @Builder.Default
    @Column(name = "is_test_bot", nullable = false)
    private Boolean isTestBot = Boolean.FALSE;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "created_at_ms", nullable = false)
    private Long createdAtMs = 0L;

    /**
     * Вложение сообщения: {kind, dataUrl, width, height, name} для картинки.
     * jsonb, потому что набор полей зависит от kind — у записи он другой.
     */
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Object attachment;
}
