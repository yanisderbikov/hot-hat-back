package ru.hothat.machine.store;

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

/**
 * Что именно тронул прогон уборки и почему.
 *
 * <p>Ответ на вопрос «почему исчезла моя комната»: сегодня причина сноса
 * приезжает вызывающему в теле ответа и нигде не остаётся.
 *
 * <p>{@link #targetId} — строка, а не ссылка, и это третий разряд колонок без
 * внешнего ключа: за одним столбцом стоят четыре разные сущности с тремя
 * разными типами ключа (комната — {@code hat-…}, запись — uuid, оба вида
 * токенов — свои). Привести их к одному типу нечем, а четыре nullable-колонки
 * со взаимоисключающими ключами были бы хуже: три из них всегда пусты.
 */
@Entity
@Table(name = "maintenance_run_target", schema = "v2")
@IdClass(MaintenanceRunTargetId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MaintenanceRunTarget {

    /** Что за объект; набор закрыт ограничением базы. */
    static final String ROOM = "room";
    static final String RECORDING = "recording";
    static final String REFRESH_TOKEN = "refresh_token";
    static final String PASSWORD_RESET_TOKEN = "password_reset_token";

    /** Что с ним сделали; набор закрыт ограничением базы. */
    static final String DELETED = "deleted";
    static final String KEPT = "kept";
    static final String FAILED = "failed";

    @Id
    @Column(name = "run_id", nullable = false, updatable = false)
    private Long runId;

    @Id
    @Column(name = "target_kind", nullable = false, updatable = false, length = 24)
    private String targetKind;

    @Id
    @Column(name = "target_id", nullable = false, updatable = false, length = 180)
    private String targetId;

    @Column(nullable = false, updatable = false, length = 16)
    private String outcome;

    /**
     * Почему прогон решил именно так: no-human-players, all-humans-left,
     * stale-10m, abandoned-game, saved-by-player, expired.
     */
    @Column(updatable = false, length = 60)
    private String reason;

    @Builder.Default
    @Column(name = "at", nullable = false, updatable = false)
    private Instant at = Instant.now();
}
