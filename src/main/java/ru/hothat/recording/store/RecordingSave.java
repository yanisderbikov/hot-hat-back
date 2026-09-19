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
 * Кто положил запись к себе.
 *
 * <p>Заменяет jsonb-массив {@code saved_by}. Массив приходилось искать
 * оператором {@code @>} нативным запросом по всей таблице записей, и с ним же
 * расходился счётчик {@code saved_count}.
 *
 * <p>Ключ на учётку здесь ЕСТЬ, в отличие от участника записи: сохранить
 * запись может только человек, у тест-бота нет ни библиотеки, ни учётки.
 */
@Entity
@Table(name = "recording_save", schema = "v2")
@IdClass(RecordingSaveId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RecordingSave {

    @Id
    @Column(name = "recording_id", nullable = false, updatable = false)
    private UUID recordingId;

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    @Builder.Default
    @Column(name = "saved_at", nullable = false, updatable = false)
    private Instant savedAt = Instant.now();
}
