package ru.hothat.profile.store;

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
import java.util.UUID;

/**
 * Где игрок сейчас и когда его видели. Самая горячая строка кластера.
 *
 * <p>Пинг приходит раз в 30–60 секунд от каждого, кто открыл портал. Пока это
 * была колонка {@code app_user.last_seen_at}, каждый такой пинг переписывал
 * строку с паролем, согласиями и аватаркой целиком.
 *
 * <p>{@code @Version} здесь намеренно нет (§6.4): побеждает последняя запись,
 * и это верная семантика — «видели в 12:00:30» после «видели в 12:00:00» не
 * конфликт, а обновление. Таблица объявлена с {@code fillfactor 70}, чтобы
 * обновление проходило внутри той же страницы и не трогало индексы.
 *
 * <p>{@link #activeRoomId} — строковый код комнаты вида {@code hat-<hex16>}, и
 * внешнего ключа на него нет: таблица комнаты новой схемы ещё не существует,
 * а ссылаться на сегодняшнюю нельзя — у неё другой владелец.
 */
@Entity
@Table(name = "player_presence", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class PlayerPresence {

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    @Builder.Default
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();

    @Column(name = "active_room_id", length = 24)
    private String activeRoomId;

    /** Ставится и снимается вместе с комнатой: этого требует ограничение базы. */
    @Column(name = "active_room_at")
    private Instant activeRoomAt;
}
