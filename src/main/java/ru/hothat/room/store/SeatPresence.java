package ru.hothat.room.store;

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
 * Сердцебиение места и состояние устройств. Самая горячая строка кластера.
 *
 * <p>Отметка приходит с каждого места примерно раз в десять секунд, а
 * состояние камеры и микрофона — на каждое переключение. Пока это были
 * колонки строки места, каждый такой пинг переписывал её вместе с аватаром на
 * 140 000 символов и боезапасом. Таблица отделена ровно по этой причине и
 * заведена с {@code fillfactor 70}, как {@code v2.player_presence} в V13:
 * место под новую версию строки остаётся в самой странице, обновление идёт
 * HOT-путём и не трогает индексы.
 *
 * <p>Строка общая для игрока и для зрителя: зритель тоже шлёт отметку, и
 * заводить ей вторую таблицу значило бы объединять их через UNION в каждом
 * подсчёте живых.
 *
 * <p>{@code @Version} здесь намеренно нет (§6.4): побеждает последняя запись,
 * и это верная семантика — «видели в 12:00:30» после «видели в 12:00:00» не
 * конфликт, а обновление.
 */
@Entity
@Table(name = "room_presence", schema = "v2")
@IdClass(SeatPresenceId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class SeatPresence {

    @Id
    @Column(name = "room_id", nullable = false, updatable = false, length = 24)
    private String roomId;

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    @Builder.Default
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();

    @Builder.Default
    @Column(name = "camera_on", nullable = false)
    private Boolean cameraOn = false;

    @Builder.Default
    @Column(name = "mic_on", nullable = false)
    private Boolean micOn = false;

    /**
     * Поколение медиа-настроек: по нему собеседники понимают, что дорожку
     * нужно пересобрать. Растёт атомарным {@code UPDATE}.
     */
    @Builder.Default
    @Column(name = "media_revision", nullable = false)
    private Long mediaRevision = 0L;

    @Column(name = "media_ready_at")
    private Instant mediaReadyAt;
}
