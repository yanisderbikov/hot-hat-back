package ru.hothat.sabotage.store;

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
 * Стартовая обойма учётки: ровно пять строк на игрока.
 *
 * <p>Сегодня это jsonb-массив {@code default_meme_loadout} в карточке игрока.
 * Замена одного мема переписывала весь массив, а «в скольких обоймах стоит
 * этот мем» — вопрос, на который массив не отвечал вовсе: при снятии мема с
 * публикации приходилось перебирать всех игроков.
 *
 * <p>{@link #memeId} без внешнего ключа: таблицы мемов новой схемы ещё нет
 * (кластер media). Индекс по нему заведён заранее — он и есть тот ответ, ради
 * которого мем нельзя удалить молча.
 */
@Entity
@Table(name = "player_default_loadout", schema = "v2")
@IdClass(DefaultLoadoutSlotId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class DefaultLoadoutSlot {

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    /** 0..4 — набор закрыт ограничением базы. */
    @Id
    @Column(name = "slot_index", nullable = false, updatable = false)
    private Integer slotIndex;

    @Column(name = "meme_id", nullable = false)
    private UUID memeId;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
