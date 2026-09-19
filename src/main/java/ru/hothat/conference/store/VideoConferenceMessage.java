package ru.hothat.conference.store;

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
 * Одно сообщение чата видео-чата: текст, файл или и то и другое.
 *
 * <p>Идентификатор выдаёт база, и он же — порядок ленты: две отметки времени
 * могут совпасть до миллисекунды, номер — нет.
 */
@Entity
@Table(name = "video_conference_message", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class VideoConferenceMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "conference_id", nullable = false, updatable = false, length = 24)
    private String conferenceId;

    @Column(name = "sender_player_id", nullable = false, updatable = false)
    private UUID sender;

    @Column(name = "body", length = 1000)
    private String body;

    @Column(name = "file_key", length = 320)
    private String fileKey;

    @Column(name = "file_name", length = 160)
    private String fileName;

    @Column(name = "file_mime", length = 120)
    private String fileMime;

    @Column(name = "file_size")
    private Long fileSize;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
