package ru.hothat.model.room;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** rooms/{id}/spectators/{uid}. */
@Entity
@Table(name = "room_spectator")
@IdClass(RoomMemberId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomSpectator {

    @Id
    @Column(name = "room_id", nullable = false, length = 80)
    private String roomId;

    @Id
    @Column(nullable = false, length = 160)
    private String uid;

    @Column(length = 80)
    private String name;

    @Column(name = "avatar_data_url", columnDefinition = "text")
    private String avatarDataUrl;

    @Builder.Default
    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    @Builder.Default
    @Column(name = "last_seen_at", nullable = false)
    private Long lastSeenAt = 0L;

    /** Всегда "spectator": клиент шлёт роль явно, чтобы отличать от игрока. */
    @Column(length = 24)
    private String role;

    /**
     * Лёгкая подписка ради превью комнаты на главной, а не полноценный зритель:
     * такой «зритель» не занимает место и не показывается в составе.
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean preview = Boolean.FALSE;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
