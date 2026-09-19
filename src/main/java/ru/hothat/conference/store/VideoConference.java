package ru.hothat.conference.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Паспорт видео-чата: хозяин, срок, состояние и заведённая из него комната.
 *
 * <p>Класс не публичный намеренно: сущность не выходит за пределы
 * {@code conference.store} — ни в сигнатуры сценариев, ни в DTO. Тот же
 * приём, что у {@code friend.store.Friendship}.
 */
@Entity
@Table(name = "video_conference", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class VideoConference {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 24)
    private String id;

    @Column(name = "host_player_id", nullable = false, updatable = false)
    private UUID host;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 16)
    private String status = ConferenceStore.OPEN;

    @Column(name = "game_room_id", length = 24)
    private String gameRoomId;

    @Column(name = "game_room_created_at")
    private Instant gameRoomCreatedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
