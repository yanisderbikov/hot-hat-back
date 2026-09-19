package ru.hothat.room.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
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
 * Место игрока: чьё оно и в какой команде.
 *
 * <p>Здесь нет {@code name} и {@code avatar_data_url}. Сегодня они есть, и это
 * самая дорогая копия чужих данных во всей базе: аватар до 140 000 символов
 * лежит в КАЖДОЙ строке места, тиражируется в каждый снимок комнаты и в
 * состояние рекордера, которое опрашивается четыре раза в секунду. Имя и
 * аватар читаются через {@code ProfileDirectoryPort}; вместе с копиями
 * исчезает {@code propagateNickname}, обходивший на каждую смену ника все
 * комнаты игрока.
 *
 * <p>Здесь нет и колонок диверсий (боезапас, пять списков мемов, курсор,
 * перезарядка): они принадлежат партии, а не месту в комнате, и живут в
 * {@code sabotage.store}. Сегодня выход из комнаты и возврат обнуляют
 * боезапас посреди партии просто потому, что он лежал в строке места.
 *
 * <p>Внешнего ключа на учётку у {@link #playerId} нет намеренно: место может
 * занять тест-бот, а у бота учётки нет и не будет. Признак лежит рядом,
 * в {@link #bot}.
 */
@Entity
@Table(name = "room_player", schema = "v2")
@IdClass(RoomSeatId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RoomSeat {

    @Id
    @Column(name = "room_id", nullable = false, updatable = false, length = 24)
    private String roomId;

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    /** Пусто — игрок вошёл, но за стол ещё не сел. */
    @Column(name = "team_id")
    private UUID teamId;

    @Builder.Default
    @Column(nullable = false)
    private Boolean bot = false;

    /**
     * Кто позвал. Заменяет пару {@code invited_by} + {@code invite_id}: сама
     * заявка живёт в {@link RoomInvitation}, и хранить рядом с местом ещё и её
     * номер незачем.
     */
    @Column(name = "invited_by")
    private UUID invitedBy;

    @Builder.Default
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
