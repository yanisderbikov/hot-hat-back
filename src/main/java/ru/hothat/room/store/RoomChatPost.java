package ru.hothat.room.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Сообщение чата комнаты.
 *
 * <p>Имя класса не {@code RoomChatMessage}: так называется легаси-сущность.
 *
 * <p>{@link #authorName} — снимок, а не копия чужих данных: реплика должна
 * остаться подписанной тем именем, под которым её написали, даже если человек
 * потом сменил ник или вышел из комнаты. Тем же снимком закрыт и бот: имя
 * тест-бота больше нигде не хранится.
 *
 * <p>{@link #authorSeat} заменяет пару {@code role} + {@code is_test_bot}: у
 * флага и роли было четыре сочетания, из которых осмысленны три.
 *
 * <p>Картинка лежит здесь, а не за ссылкой: это содержимое сообщения, а не
 * копия чужих данных.
 */
@Entity
@Table(name = "room_chat_message", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RoomChatPost {

    /** player | spectator | bot — набор закрыт ограничением базы. */
    static final String PLAYER = "player";
    static final String SPECTATOR = "spectator";
    static final String BOT = "bot";

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "room_id", nullable = false, updatable = false, length = 24)
    private String roomId;

    /** Пусто у системного сообщения комнаты: его автор — сервер. */
    @Column(name = "author_player_id", updatable = false)
    private UUID authorPlayerId;

    @Column(name = "author_name", nullable = false, updatable = false, length = 40)
    private String authorName;

    @Column(name = "author_seat", nullable = false, updatable = false, length = 16)
    private String authorSeat;

    /** Пусто — сообщение состоит из одной картинки. */
    @Column(length = 800)
    private String body;

    /** Предел тот же, что проверяет DTO на входе: 160 000 символов. */
    @Column(name = "image_data_url", updatable = false)
    private String imageDataUrl;

    @Column(name = "image_width", updatable = false)
    private Integer imageWidth;

    @Column(name = "image_height", updatable = false)
    private Integer imageHeight;

    @Column(name = "image_file_name", updatable = false, length = 80)
    private String imageFileName;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "edited_at")
    private Instant editedAt;

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
