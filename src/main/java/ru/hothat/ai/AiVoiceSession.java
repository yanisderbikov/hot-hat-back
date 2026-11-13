package ru.hothat.ai;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Durable, owner-only billing audit per voice activation. No audio persisted. */
@Entity
@Table(name="ai_voice_session")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiVoiceSession {
    @Id @Column(name="session_id",length=80,nullable=false)
    private String sessionId;
    @Column(name="room_id",length=80,nullable=false)
    private String roomId;
    @Column(name="game_number",nullable=false)
    private Integer gameNumber;
    @Column(name="team_id",length=80,nullable=false)
    private String teamId;
    @Column(name="bot_uid",length=160,nullable=false)
    private String botUid;
    @Column(name="turn_key",length=160,nullable=false)
    private String turnKey;
    @Builder.Default @Column(name="mode",length=16,nullable=false)
    private String mode="game";
    @Column(name="speaker_uid",length=160)
    private String speakerUid;
    @Column(name="livekit_dispatch_id",length=160)
    private String livekitDispatchId;
    @Column(nullable=false,length=32)
    private String status;
    @Column(name="started_at")
    private Instant startedAt;
    @Column(name="finished_at")
    private Instant finishedAt;
    @Column(name="last_usage_at")
    private Instant lastUsageAt;
    @Builder.Default
    @Column(name="voice_seconds",nullable=false,precision=12,scale=3)
    private BigDecimal voiceSeconds=BigDecimal.ZERO;
    @Builder.Default
    @Column(name="backend_input_tokens",nullable=false)
    private Long backendInputTokens=0L;
    @Builder.Default
    @Column(name="backend_output_tokens",nullable=false)
    private Long backendOutputTokens=0L;
    @Builder.Default
    @Column(name="estimated_cost_usd",nullable=false,precision=14,scale=6)
    private BigDecimal estimatedCostUsd=BigDecimal.ZERO;
    @Column(name="error_code",length=120)
    private String errorCode;
    @Builder.Default
    @Column(name="created_at",nullable=false)
    private Instant createdAt=Instant.now();
}
