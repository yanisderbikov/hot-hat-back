package ru.hothat.recording.store;

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
 * Кому запись открыта, кроме участников.
 *
 * <p>Заменяет jsonb-массив {@code shared_with}. Право смотреть складывается из
 * трёх источников: участник партии, сохранивший её у себя и тот, кому её
 * отправили в личной переписке. Третий — это строка здесь.
 *
 * <p>{@link #sharedBy} без ключа: доступ обязан пережить удаление учётки того,
 * кто его открыл, иначе получатель молча потерял бы запись.
 */
@Entity
@Table(name = "recording_share", schema = "v2")
@IdClass(RecordingShareId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RecordingShare {

    @Id
    @Column(name = "recording_id", nullable = false, updatable = false)
    private UUID recordingId;

    @Id
    @Column(nullable = false, updatable = false)
    private UUID grantee;

    @Column(name = "shared_by", nullable = false, updatable = false)
    private UUID sharedBy;

    @Builder.Default
    @Column(name = "shared_at", nullable = false, updatable = false)
    private Instant sharedAt = Instant.now();
}
