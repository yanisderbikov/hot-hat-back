package ru.hothat.room.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
 * История передач хозяйства комнаты.
 *
 * <p>Отдельной строкой, потому что сегодня прошедшее событие описано тремя
 * колонками ЖИВОЙ строки комнаты, и вторая передача стирает первую. Спор «кто
 * и когда забрал у меня комнату» этими колонками неразрешим.
 *
 * <p>{@code @Version} нет: журнал только дописывается.
 */
@Entity
@Table(name = "room_host_transfer", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RoomHostTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Column(name = "room_id", nullable = false, updatable = false, length = 24)
    private String roomId;

    /** Пусто у самой первой записи: до неё хозяина не было. */
    @Column(name = "from_player_id")
    private UUID fromPlayerId;

    @Column(name = "to_player_id", nullable = false)
    private UUID toPlayerId;

    /** manual | idle | departure. Причина в журнале, а не состояние. */
    @Column(nullable = false, length = 24)
    private String reason;

    @Column(name = "performed_by")
    private UUID performedBy;

    @Builder.Default
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();
}
