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
 * Крупная фаза комнаты и счётчик партий.
 *
 * <p>Это половина разрезанной {@code room.phase}. Сегодня одна колонка держит
 * восемь значений двух разных уровней сразу — «комната набирается» и «идёт
 * апелляция», — и пишут её пять сервисов. Пока значения двух уровней лежали в
 * одной колонке, правило «один писатель» было невозможно физически.
 *
 * <p>Здесь остался только уровень комнаты. Состояние хода — в
 * {@code game.store}, факт завершения партии — там же, а восьмизначная фаза
 * контракта собирается проекцией из трёх таблиц. Прямого преемника у
 * {@code phase} нет и не должно быть.
 *
 * <p>{@link #gameNumber} поднимается атомарным
 * {@code UPDATE … SET game_number = game_number + 1} под блокировкой строки
 * (§6.4): две вкладки хозяина иначе заведут две партии с одним номером.
 * Сегодня от этого спасает только {@code claimGameTab} в localStorage
 * браузера.
 */
@Entity
@Table(name = "room_lifecycle", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RoomLifecycle {

    /** setup | playing | resetting | closed — набор закрыт ограничением базы. */
    static final String SETUP = "setup";
    static final String CLOSED = "closed";

    @Id
    @Column(name = "room_id", nullable = false, updatable = false, length = 24)
    private String roomId;

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String state = SETUP;

    @Builder.Default
    @Column(name = "game_number", nullable = false)
    private Integer gameNumber = 0;

    /**
     * Питает уборку брошенных комнат. Отдельной колонкой, а не максимумом по
     * присутствию: комната жива и пока в ней просто болтают в чате.
     */
    @Builder.Default
    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "closed_reason", length = 40)
    private String closedReason;

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
