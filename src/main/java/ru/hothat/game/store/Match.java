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
 * Партия как факт.
 *
 * <p>Замороженные условия ({@link #gameMode}, {@link #ranked},
 * {@link #testRoom}, {@link #gameLanguage}, {@link #turnDurationMs})
 * скопированы из комнаты намеренно, и это не та копия чужих данных, которую
 * убирает §6.3: партия играется по правилам, действовавшим на её старте.
 * Сменить режим комнаты посреди партии и получить другой подсчёт очков задним
 * числом нельзя именно потому, что партия смотрит на свою копию.
 *
 * <p>Техническое завершение развёрнуто в колонки: сегодня это jsonb
 * {@code technical_termination}, и «была ли партия аннулирована» приходится
 * узнавать разбором json в SQL. Кого именно не хватило — строки
 * {@link PauseAbsentee}.
 *
 * <p>Уникальный частичный индекс «одна идущая партия на комнату» — тот самый
 * инвариант, который сегодня держится клиентским {@code claimGameTab} в
 * localStorage: две вкладки хозяина заводили две партии, и вторая молча
 * затирала первую.
 */
@Entity
@Table(name = "match", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Match {

    /** running | finished — набор закрыт ограничением базы. */
    static final String RUNNING = "running";
    static final String FINISHED = "finished";

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "room_id", nullable = false, updatable = false, length = 24)
    private String roomId;

    /** Номер партии в комнате; вместе с комнатой — ключ идемпотентности старта. */
    @Column(name = "game_number", nullable = false, updatable = false)
    private Integer gameNumber;

    @Column(name = "game_mode", nullable = false, updatable = false, length = 16)
    private String gameMode;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Boolean ranked = false;

    @Builder.Default
    @Column(name = "test_room", nullable = false, updatable = false)
    private Boolean testRoom = false;

    @Column(name = "game_language", nullable = false, updatable = false, length = 8)
    private String gameLanguage;

    @Column(name = "turn_duration_ms", nullable = false, updatable = false)
    private Integer turnDurationMs;

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String state = RUNNING;

    /** bag_empty | technical | room_closed | host_reset — причина в протоколе. */
    @Column(name = "finish_reason", length = 24)
    private String finishReason;

    /** ranked_disconnect | casual_disconnect; пусто — партия доиграна честно. */
    @Column(name = "termination_kind", length = 24)
    private String terminationKind;

    @Builder.Default
    @Column(name = "termination_no_penalty", nullable = false)
    private Boolean terminationNoPenalty = false;

    @Builder.Default
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
