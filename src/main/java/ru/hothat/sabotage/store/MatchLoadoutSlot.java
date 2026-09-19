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

import java.util.UUID;

/**
 * Обойма партии: пять слотов и круг, по которому они ходят.
 *
 * <p>Заменяет ПЯТЬ jsonb-массивов строки места сразу: {@code meme_loadout},
 * {@code meme_available_ids}, {@code meme_reserve_ids},
 * {@code meme_recycle_queue}, {@code used_meme_ids}. Все пять описывают одни и
 * те же пять мемов, поэтому один мем лежал в документе до пяти раз, а
 * согласовывать списки приходилось руками — правило выдачи выбрасывало из
 * очередей то, чего нет в обойме, то есть чинило расхождение на каждом чтении.
 *
 * <p>Здесь мем — одна строка. {@link #bucket} говорит, в какой части круга он
 * сейчас: {@code available} (готов к выстрелу) → {@code recycle} (потрачен) →
 * снова {@code available}, когда кончится {@code reserve}. Уникальность
 * «мем в обойме один раз» и делает пять списков разбиением, а не пятью
 * копиями.
 *
 * <p>Списка потраченных здесь нет: «какие мемы я уже показал» — это строки
 * журнала выстрелов, и отдельный массив был его копией.
 */
@Entity
@Table(name = "match_loadout_slot", schema = "v2")
@IdClass(MatchLoadoutSlotId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MatchLoadoutSlot {

    /** available | reserve | recycle — набор закрыт ограничением базы. */
    static final String AVAILABLE = "available";
    static final String RESERVE = "reserve";
    static final String RECYCLE = "recycle";

    @Id
    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    /** 0..4 — место мема в обойме, оно не меняется за партию. */
    @Id
    @Column(name = "slot_index", nullable = false, updatable = false)
    private Integer slotIndex;

    @Column(name = "meme_id", nullable = false, updatable = false)
    private UUID memeId;

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String bucket = RESERVE;

    /** Место в своей очереди: по нему выдаётся следующий мем. */
    @Builder.Default
    @Column(name = "bucket_order", nullable = false)
    private Integer bucketOrder = 0;
}
