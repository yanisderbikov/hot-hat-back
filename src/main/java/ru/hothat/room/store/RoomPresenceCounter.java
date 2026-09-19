package ru.hothat.room.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Счётчик живых игроков для витрины комнат.
 *
 * <p>Его пишет хозяин комнаты раз в несколько секунд, а читает главная
 * страница списком по всем комнатам сразу. Отдельной строкой, потому что это
 * единственное поле комнаты, которое пишет один участник, а читают все, и
 * держать его в паспорте значит переписывать паспорт по таймеру.
 *
 * <p>{@code @Version} нет по той же причине, что у {@link SeatPresence}:
 * побеждает последнее измерение.
 */
@Entity
@Table(name = "room_presence_counter", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RoomPresenceCounter {

    @Id
    @Column(name = "room_id", nullable = false, updatable = false, length = 24)
    private String roomId;

    @Builder.Default
    @Column(name = "active_players", nullable = false)
    private Integer activePlayers = 0;

    @Builder.Default
    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt = Instant.now();
}
