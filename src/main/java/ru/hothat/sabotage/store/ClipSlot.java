package ru.hothat.sabotage.store;

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
 * Слот подменного клипа: десять секунд объясняющего, снятые заранее.
 *
 * <p>Заменяет jsonb {@code replacement_recordings} — карту «id клипа →
 * объект» в строке комнаты. Съёмка идёт около десяти секунд, и всё это время
 * карта переписывалась вместе с комнатой; два игрока, снимавших одновременно,
 * теряли один клип.
 *
 * <p>Имя класса не {@code ReplacementClip}: так называется запись движка
 * {@code ru.hothat.game.domain.ReplacementClip}.
 *
 * <p>{@link #state}, а не пара флагов: §6.4 требует условных переходов
 * ({@code UPDATE … WHERE state = 'recording'}), потому что {@code @Version} не
 * умеет отличить «уже готов» от «уже выброшен» — оба выглядят как устаревшая
 * версия строки.
 *
 * <p>{@link #recordDeadline} — срок, после которого начатая и брошенная
 * съёмка считается потерянной (вкладку закрыли, итог не пришёл). Сегодня это
 * константа в коде и вычитание дат при каждом чтении.
 *
 * <p>{@link #recordedTurnId} — не украшение: применить клип в том же ходу, где
 * он снят, значит показать зрителям то, что они только что видели вживую.
 */
@Entity
@Table(name = "replacement_clip", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ClipSlot {

    /** recording | ready | consumed | discarded — набор закрыт базой. */
    static final String RECORDING = "recording";
    static final String READY = "ready";
    static final String CONSUMED = "consumed";
    static final String DISCARDED = "discarded";

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Column(name = "attacker_player_id", nullable = false, updatable = false)
    private UUID attackerPlayerId;

    @Column(name = "target_player_id", nullable = false, updatable = false)
    private UUID targetPlayerId;

    @Column(name = "recorded_turn_id", nullable = false, updatable = false)
    private UUID recordedTurnId;

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String state = RECORDING;

    @Column(name = "record_deadline", nullable = false, updatable = false)
    private Instant recordDeadline;

    @Column(name = "ready_at")
    private Instant readyAt;

    /** Выстрел, в котором клип показали; пусто — ещё не показан. */
    @Column(name = "consumed_event_id")
    private UUID consumedEventId;

    @Column(name = "discarded_at")
    private Instant discardedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
