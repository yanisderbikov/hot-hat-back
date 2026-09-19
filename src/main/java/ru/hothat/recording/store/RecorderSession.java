package ru.hothat.recording.store;

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
 * Отметки страницы-рекордера.
 *
 * <p>Заменяет шесть колонок {@code game_recording}: {@code recorder_ready_at_ms},
 * {@code recorder_ready_phase}, {@code recorder_start_signal_at_ms},
 * {@code recorder_start_phase}, {@code recorder_livekit_identity},
 * {@code prewarmed}. Писатель у них один и чужой всем остальным — сама
 * страница рекордера, машинная поверхность.
 *
 * <p>Три сегодняшних мутирующих GET ({@code ready=1}, {@code started=1},
 * {@code ceremony_done=1}) стали POST-сигналами, но в базе это по-прежнему три
 * отметки времени: важно не «сколько раз позвали», а «дошёл ли рекордер до
 * этого шага».
 *
 * <p>{@code @Version} (§6.4): сигналы приходят от рекордера, а фазу партии
 * рядом с ними пишет сервер, и две записи в одну строку встречаются.
 */
@Entity
@Table(name = "recorder_session", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RecorderSession {

    @Id
    @Column(name = "recording_id", nullable = false, updatable = false)
    private UUID recordingId;

    /** Съёмку подняли заранее, до старта партии. */
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Boolean prewarm = false;

    /**
     * Личность рекордера в комнате LiveKit: по ней запись отличает свою
     * дорожку от участников.
     */
    @Column(name = "livekit_identity", length = 220)
    private String livekitIdentity;

    @Column(name = "ready_at")
    private Instant readyAt;

    /** Фаза комнаты в момент сигнала: нужна только диагностике. */
    @Column(name = "ready_phase", length = 24)
    private String readyPhase;

    @Column(name = "start_signal_at")
    private Instant startSignalAt;

    @Column(name = "start_phase", length = 24)
    private String startPhase;

    @Column(name = "ceremony_completed_at")
    private Instant ceremonyCompletedAt;

    @Builder.Default
    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt = Instant.now();

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
