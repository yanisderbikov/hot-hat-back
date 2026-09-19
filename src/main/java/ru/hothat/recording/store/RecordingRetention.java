package ru.hothat.recording.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Срок хранения записи и счётчик сохранений.
 *
 * <p>Своя строка, потому что пишут её ИГРОКИ — каждый, кто кладёт запись к
 * себе, — а не тот, кто снимал. Сегодня это {@code expires_at_ms} и
 * {@code saved_count} внутри строки на шестьдесят три колонки: сохранение
 * записи переписывало вместе с собой состояние Egress и отметки рекордера.
 *
 * <p>{@link #saveCount} — атомарный счётчик (§6.4):
 * {@code UPDATE … SET save_count = save_count + 1} без чтения в память.
 * Сегодня счётчиков два — {@code saved_count} и длина массива {@code saved_by},
 * — и при одновременном сохранении двумя игроками они расходятся.
 *
 * <p>{@link #expiresAt} пуст ровно тогда, когда запись кто-то сохранил: база
 * это проверяет. Запись без срока и без сохранивших жила бы в бакете вечно.
 */
@Entity
@Table(name = "recording_retention", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RecordingRetention {

    @Id
    @Column(name = "recording_id", nullable = false, updatable = false)
    private UUID recordingId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Builder.Default
    @Column(name = "save_count", nullable = false)
    private Integer saveCount = 0;

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
