package ru.hothat.room.store;

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
 * Кто хозяин комнаты сейчас.
 *
 * <p>Вторая половина разрезанного {@code created_by}. Хозяйство меняется
 * передачей вручную и по бездействию, авторство — никогда, и держать их в
 * одной колонке значит не уметь ответить «кто завёл комнату» после первой же
 * передачи.
 *
 * <p>{@link #reclaimPlayerId} — прежний хозяин, за которым право вернуться.
 * Сегодня рядом с ним лежат ещё три колонки строки комнаты
 * ({@code host_transferred_at}, {@code host_transfer_type},
 * {@code host_transferred_by}), описывающие одно прошедшее событие; событие
 * уехало в {@link RoomHostTransfer}, где вторая передача не стирает первую.
 *
 * <p>Строка берётся {@code SELECT … FOR UPDATE} при передаче по бездействию
 * (§6.4): иначе двое одновременно объявят хозяином себя.
 */
@Entity
@Table(name = "room_host", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RoomHost {

    @Id
    @Column(name = "room_id", nullable = false, updatable = false, length = 24)
    private String roomId;

    @Column(name = "host_player_id", nullable = false)
    private UUID hostPlayerId;

    @Column(name = "reclaim_player_id")
    private UUID reclaimPlayerId;

    /**
     * Сердцебиение хозяина: по нему считается бездействие. Пишется чаще всего
     * в этой таблице, потому и лежит здесь, а не в паспорте комнаты.
     */
    @Builder.Default
    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt = Instant.now();

    /**
     * Чем именно хозяин подтвердил присутствие. Набор закрывает перечисление
     * {@code HostActivityKind} на входе, а не база: здесь это пометка в
     * журнале, и новый вид активности не должен стоить миграции.
     */
    @Column(name = "last_activity_kind", length = 16)
    private String lastActivityKind;

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
