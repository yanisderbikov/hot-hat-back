package ru.hothat.game.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
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
 * Пауза: строка есть — пауза есть.
 *
 * <p>Сегодня это шесть колонок строки комнаты ({@code game_paused},
 * {@code host_paused}, {@code pause_reason}, {@code pause_started_at_ms},
 * {@code paused_turn_remaining_ms}, {@code paused_appeal_remaining_ms}) плюс
 * два jsonb со списками отсутствующих. Из-за одной пары флагов «пауза
 * хозяина» и «пауза по обрыву связи» затирали друг друга: хозяин снимал свою
 * паузу и снимал заодно чужую, а игра продолжалась без половины игроков.
 *
 * <p>Ключ {@code (match_id, kind)} делает их независимыми: партия идёт, когда
 * строк нет ни одной.
 */
@Entity
@Table(name = "match_pause", schema = "v2")
@IdClass(MatchPauseId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MatchPause {

    /** host | disconnect — набор закрыт ограничением базы. */
    static final String HOST = "host";
    static final String DISCONNECT = "disconnect";

    @Id
    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Id
    @Column(nullable = false, updatable = false, length = 16)
    private String kind;

    /** Сколько оставалось от хода в момент остановки. */
    @Builder.Default
    @Column(name = "turn_remaining_ms", nullable = false)
    private Integer turnRemainingMs = 0;

    @Builder.Default
    @Column(name = "appeal_remaining_ms", nullable = false)
    private Integer appealRemainingMs = 0;

    @Builder.Default
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    /** Пусто у паузы по обрыву связи: её никто не ставил руками. */
    @Column(name = "started_by", updatable = false)
    private UUID startedBy;

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
