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
 * Занятость канала эффекта на сцене.
 *
 * <p>Заменяет jsonb {@code sabotage_locks} — объект с пятью сроками, номером
 * хода и вложенной картой съёмок. Два выстрела по РАЗНЫМ каналам переписывали
 * один и тот же объект целиком, и один из них терялся: эффект применялся, а
 * канал оставался свободным.
 *
 * <p>Здесь канал — строка, и она же цель {@code SELECT … FOR UPDATE}. Порядок
 * взятия каналов внутри партии — по алфавиту (§6.4), иначе два выстрела по
 * разным каналам дают клинч.
 *
 * <p>Шестого канала (пустого) нет: помидор, мем и пук ничего на сцене не
 * занимают, и строки им не нужно.
 *
 * <p>Поимённая занятость съёмки Подмены сюда НЕ переехала: это срок в строке
 * самого клипа ({@link ClipSlot#getRecordDeadline()}), потому что снимать
 * разных объясняющих одновременно вправе десять игроков, а общий срок такого
 * не выражает.
 *
 * <p>{@code @Version} нет: строку берут под пессимистичным замком.
 */
@Entity
@Table(name = "match_effect_lock", schema = "v2")
@IdClass(MatchEffectLockId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MatchEffectLock {

    /** video | voice | crocodile | overlay | replacement — набор закрыт базой. */
    static final String VIDEO = "video";
    static final String VOICE = "voice";
    static final String CROCODILE = "crocodile";
    static final String OVERLAY = "overlay";
    static final String REPLACEMENT = "replacement";

    @Id
    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Id
    @Column(nullable = false, updatable = false, length = 16)
    private String channel;

    /**
     * Срок, а не флаг «занято»: снимать блокировку некому — поставивший
     * эффект игрок может выйти, и висящий флаг остался бы навсегда.
     */
    @Column(name = "locked_until", nullable = false)
    private Instant lockedUntil;

    /**
     * Ход, в котором канал занят: по нему Подмена узнаёт «этот клип снят в
     * текущем ходу, показывать его нельзя».
     */
    @Column(name = "turn_id")
    private UUID turnId;
}
