package ru.hothat.conference.store;

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
 * Участие игрока в видео-чате: одна строка на пару, ключ — сама пара.
 *
 * <p>Приглашение — не отдельная сущность, а состояние этой строки:
 * {@code invited} → {@code member} | {@code declined}; {@code member} →
 * {@code left} | {@code removed}. Повторное приглашение ушедшего возвращает
 * строку в {@code invited}, и второй строки для того же человека не бывает.
 */
@Entity
@Table(name = "video_conference_member", schema = "v2")
@IdClass(VideoConferenceMemberId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class VideoConferenceMember {

    @Id
    @Column(name = "conference_id", nullable = false, updatable = false, length = 24)
    private String conferenceId;

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID player;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "inviter_player_id")
    private UUID inviter;

    @Builder.Default
    @Column(name = "game_room_seat", nullable = false)
    private boolean gameRoomSeat = false;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "answered_at")
    private Instant answeredAt;
}
