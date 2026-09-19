package ru.hothat.model.social;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * roomInvites/{id}: приглашение друга в свою комнату. gameNumberAtInvite
 * фиксирует поколение партии — после старта игры приглашение недействительно.
 */
@Entity
@Table(name = "room_invite")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomInvite {

    @Id
    @Column(nullable = false, length = 120)
    private String id;

    @Column(name = "room_id", nullable = false, length = 80)
    private String roomId;

    @Column(name = "room_name", length = 160)
    private String roomName;

    @Column(name = "from_uid", nullable = false, length = 160)
    private String fromUid;

    @Column(name = "from_nickname", length = 40)
    private String fromNickname;

    @Column(name = "to_uid", nullable = false, length = 160)
    private String toUid;

    @Builder.Default
    @Column(nullable = false, length = 24)
    private String status = "pending";

    @Builder.Default
    @Column(name = "game_number_at_invite", nullable = false)
    private Integer gameNumberAtInvite = 0;

    @Column(name = "chat_id", length = 340)
    private String chatId;

    @Column(name = "message_id", length = 80)
    private String messageId;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "created_at_ms", nullable = false)
    private Long createdAtMs = 0L;

    @Builder.Default
    @Column(name = "expires_at_ms", nullable = false)
    private Long expiresAtMs = 0L;
}
