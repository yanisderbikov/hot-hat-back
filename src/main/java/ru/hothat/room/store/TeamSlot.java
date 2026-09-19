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
 * Команда как место в составе комнаты.
 *
 * <p>Имя класса не {@code RoomTeam}: так называется легаси-сущность
 * {@code ru.hothat.model.room.RoomTeam}.
 *
 * <p>Колонки {@code score} здесь нет: §6.3 плана числит её выводимой, и это
 * буквально так — счёт команды есть число незачёркнутых угаданных слов её
 * ходов. Денормализованный счётчик рядом с исходными строками умеет с ними
 * разойтись, а разойдясь, показывает игроку неверный счёт партии.
 *
 * <p>Колонки {@code member_uids} тоже нет: состав — это
 * {@link RoomSeat#getTeamId()}. Сегодня он лежит в двух местах сразу.
 *
 * <p>{@link #slot} — место в очереди ходов вместо отдельного массива
 * {@code team_order} в строке комнаты, то есть второго источника правды о
 * порядке. Уникальность {@code (room_id, slot)} делает невозможными две
 * команды на одном месте.
 */
@Entity
@Table(name = "room_team", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class TeamSlot {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "room_id", nullable = false, updatable = false, length = 24)
    private String roomId;

    @Column(nullable = false)
    private Integer slot;

    @Column(length = 40)
    private String name;

    /** Постоянная команда, стоящая за этим местом; пусто — случайный состав. */
    @Column(name = "ranked_team_id")
    private UUID rankedTeamId;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
