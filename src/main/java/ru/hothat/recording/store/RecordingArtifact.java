package ru.hothat.recording.store;

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
 * Файл записи в хранилище.
 *
 * <p>Отдельно от паспорта, потому что появляется позже всех и от другого
 * писателя: путь, размер и длительность приезжают в теле вебхука о завершении
 * выгрузки.
 *
 * <p>{@link #deletedAt} — мягкое удаление. Строка записи переживает свой файл:
 * иначе ссылка на запись в личной переписке указывала бы в пустоту, а карточка
 * в чужой библиотеке исчезала бы без объяснения.
 *
 * <p>{@link #durationNs} в наносекундах — так длительность отдаёт LiveKit, и
 * так её ждёт карточка. Переводить её в миллисекунды здесь значило бы потерять
 * точность ради красоты хранения.
 */
@Entity
@Table(name = "recording_artifact", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RecordingArtifact {

    @Id
    @Column(name = "recording_id", nullable = false, updatable = false)
    private UUID recordingId;

    @Column(name = "object_path", nullable = false, length = 500)
    private String objectPath;

    @Column(name = "storage_bucket", length = 120)
    private String storageBucket;

    @Builder.Default
    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes = 0L;

    @Builder.Default
    @Column(name = "duration_ns", nullable = false)
    private Long durationNs = 0L;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
