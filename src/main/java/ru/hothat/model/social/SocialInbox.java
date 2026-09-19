package ru.hothat.model.social;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * socialInboxes/{uid}: одна строка на игрока, по её версиям фронтенд понимает,
 * что появилось новое сообщение или приглашение, не читая всю переписку.
 */
@Entity
@Table(name = "social_inbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialInbox {

    @Id
    @Column(nullable = false, length = 160)
    private String uid;

    @Builder.Default
    @Column(name = "message_version", nullable = false)
    private Long messageVersion = 0L;

    @Builder.Default
    @Column(name = "unread_messages", nullable = false)
    private Integer unreadMessages = 0;

    @Builder.Default
    @Column(name = "last_message_at_ms", nullable = false)
    private Long lastMessageAtMs = 0L;

    @Column(name = "last_message_from_uid", length = 160)
    private String lastMessageFromUid;

    @Column(name = "last_message_from_nickname", length = 40)
    private String lastMessageFromNickname;

    @Column(name = "last_message_text", length = 200)
    private String lastMessageText;

    @Builder.Default
    @Column(name = "room_invite_version", nullable = false)
    private Long roomInviteVersion = 0L;

    @Column(name = "room_invite_id", length = 120)
    private String roomInviteId;

    @Column(name = "room_invite_room_id", length = 80)
    private String roomInviteRoomId;

    @Column(name = "room_invite_room_name", length = 160)
    private String roomInviteRoomName;

    @Column(name = "room_invite_from_uid", length = 160)
    private String roomInviteFromUid;

    @Column(name = "room_invite_from_nickname", length = 40)
    private String roomInviteFromNickname;

    @Column(name = "room_invite_status", length = 24)
    private String roomInviteStatus;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
