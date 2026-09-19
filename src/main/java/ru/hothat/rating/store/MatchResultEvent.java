package ru.hothat.rating.store;

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
 * Факт зачёта партии — барьер идемпотентности.
 *
 * <p>Ключ составной, а не склеенный: сегодня это строка «roomId-gameNumber» в
 * VARCHAR(120), и проверка «уже засчитано?» — чтение перед вставкой. Два
 * клиента, дожавшие кнопку одновременно, начисляли очки дважды. Здесь второй
 * зачёт отвергает сама база.
 *
 * <p>{@link #outcome} и {@link #technical} — разные вещи, вопреки строке
 * §6.3 плана, где они слиты в одно перечисление: контракт отдаёт и то и
 * другое, и признак технического завершения есть у засчитанной партии тоже.
 * «Уже засчитано» состоянием не является — это отсутствие вставки.
 *
 * <p>Внешнего ключа на комнату нет: новой таблицы комнат ещё нет, а ссылаться
 * на старую нельзя — там другой тип ключа и другой владелец. Формат
 * идентификатора комнаты держит ограничение базы.
 */
@Entity
@Table(name = "match_result_event", schema = "v2")
@IdClass(MatchResultEventId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MatchResultEvent {

    @Id
    @Column(name = "room_id", nullable = false, updatable = false, length = 24)
    private String roomId;

    @Id
    @Column(name = "game_number", nullable = false, updatable = false)
    private Integer gameNumber;

    @Column(name = "board_id", nullable = false, updatable = false)
    private Long boardId;

    /** recorded | annulled — набор закрыт ограничением базы. */
    @Column(nullable = false, length = 16)
    private String outcome;

    @Builder.Default
    @Column(nullable = false)
    private Boolean technical = Boolean.FALSE;

    /** Почему партия завершилась так: например, {@code mass_disconnect}. */
    @Column(length = 60)
    private String reason;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private UUID recordedBy;

    @Builder.Default
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt = Instant.now();
}
