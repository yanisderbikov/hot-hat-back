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
 * Включена ли запись партий в комнате.
 *
 * <p>Единственная сущность кластера, живущая при комнате, а не при записи, и
 * это разрешение расхождения §6.1: колонки {@code record_game},
 * {@code recording_preference_updated_at} и
 * {@code recording_preference_updated_by} убраны из {@code room}, потому что
 * флаг ставит хозяин комнаты РАДИ ЗАПИСИ, и писатель у него — область
 * recording. Пока флаг лежал в строке комнаты, его переписывало любое
 * обновление комнаты: у {@code Room} нет частичного обновления, и засчитанное
 * слово уносило в базу все её колонки разом.
 *
 * <p>{@code @Version} нет: флаг переключает один человек — хозяин комнаты, —
 * и последняя запись здесь и есть верный ответ.
 */
@Entity
@Table(name = "room_recording_policy", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RoomRecordingPolicy {

    @Id
    @Column(name = "room_id", nullable = false, updatable = false, length = 24)
    private String roomId;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = false;

    /** Кто трогал флаг последним; без ключа, это авторство. */
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
