package ru.hothat.game.store;

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
 * Ход как сущность.
 *
 * <p>Сегодня ход — это одиннадцать колонок строки комнаты ({@code turn_id},
 * {@code turn_started_at}, {@code turn_ends_at}, {@code current_team_id},
 * {@code current_team_index}, {@code current_word},
 * {@code current_turn_score}, {@code explainer_uid}, {@code explainer_name},
 * {@code guesser_uid}, {@code guesser_name}) плюс jsonb с угаданными словами.
 * Каждое засчитанное слово переписывало их вместе со всей комнатой: с
 * настройками, составами, приглашениями и мешком.
 *
 * <p>Счёта хода здесь нет: он есть число незачёркнутых строк
 * {@link TurnWord} этого хода. Имён объясняющего и угадывающего тоже нет —
 * они в замороженном ростере.
 *
 * <p>{@link #deadlineAt} — один срок вместо трёх представлений одного отрезка
 * времени, которые расходились после каждой паузы.
 *
 * <p>Строка берётся {@code SELECT … FOR UPDATE} на каждом действии со словом
 * (§6.4): объясняющий спорит со сторожевым таймером истечения, и под этой же
 * блокировкой пропущенному слову назначается новая позиция в колоде.
 */
@Entity
@Table(name = "match_turn", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MatchTurn {

    /** intro | active | appeal | review | closed — набор закрыт базой. */
    static final String INTRO = "intro";
    static final String ACTIVE = "active";
    static final String APPEAL = "appeal";
    static final String REVIEW = "review";
    static final String CLOSED = "closed";

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Column(name = "turn_no", nullable = false, updatable = false)
    private Integer turnNo;

    @Column(name = "room_team_id", nullable = false, updatable = false)
    private UUID roomTeamId;

    @Column(name = "explainer_player_id", nullable = false, updatable = false)
    private UUID explainerPlayerId;

    /** Пусто, если в команде остался один игрок: угадывать некому. */
    @Column(name = "guesser_player_id", updatable = false)
    private UUID guesserPlayerId;

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String state = INTRO;

    /** Слово на руках у объясняющего; пусто — ход ещё не начат или уже закрыт. */
    @Column(name = "current_word_id")
    private UUID currentWordId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "deadline_at")
    private Instant deadlineAt;

    /** Длительность именно этого хода: после паузы она равна остатку. */
    @Column(name = "duration_ms", nullable = false)
    private Integer durationMs;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
