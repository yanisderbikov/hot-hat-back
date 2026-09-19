package ru.hothat.model.media;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * gameRecordings/{roomId-gameNumber}: состояние Egress-записи партии.
 * Сам MP4 лежит в S3 по objectPath, здесь — жизненный цикл и права доступа.
 */
@Entity
@Table(name = "game_recording")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameRecording {

    @Id
    @Column(nullable = false, length = 180)
    private String id;

    @Column(name = "room_id", length = 80)
    private String roomId;

    @Builder.Default
    @Column(name = "game_number", nullable = false)
    private Integer gameNumber = 0;

    @Column(name = "room_name", length = 160)
    private String roomName;

    @Builder.Default
    @Column(name = "game_mode", nullable = false, length = 24)
    private String gameMode = "classic";

    @Builder.Default
    @Column(nullable = false)
    private Boolean ranked = Boolean.FALSE;

    @Builder.Default
    @Column(name = "is_private", nullable = false)
    private Boolean isPrivate = Boolean.FALSE;

    @Builder.Default
    @Column(name = "is_test_room", nullable = false)
    private Boolean isTestRoom = Boolean.FALSE;

    @Builder.Default
    @Column(name = "division_language", nullable = false, length = 8)
    private String divisionLanguage = "ru";

    @Builder.Default
    @Column(name = "game_language", nullable = false, length = 8)
    private String gameLanguage = "ru";

    @Builder.Default
    @Type(JsonType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> participants = new ArrayList<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "participant_uids", nullable = false, columnDefinition = "jsonb")
    private List<String> participantUids = new ArrayList<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> teams = new ArrayList<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "winner_team_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> winnerTeamIds = new ArrayList<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "winner_team_names", nullable = false, columnDefinition = "jsonb")
    private List<String> winnerTeamNames = new ArrayList<>();

    @Builder.Default
    @Column(name = "winning_score", nullable = false)
    private Integer winningScore = 0;

    @Builder.Default
    @Column(name = "total_score", nullable = false)
    private Integer totalScore = 0;

    @Builder.Default
    @Column(name = "word_count", nullable = false)
    private Integer wordCount = 0;

    @Builder.Default
    @Column(nullable = false, length = 24)
    private String status = "starting";

    @Column(name = "egress_id", length = 120)
    private String egressId;

    @Column(name = "livekit_status")
    private Integer livekitStatus;

    @Column(name = "object_path", length = 500)
    private String objectPath;

    @Column(name = "storage_provider", length = 40)
    private String storageProvider;

    @Column(name = "storage_bucket", length = 120)
    private String storageBucket;

    @Builder.Default
    @Column(name = "started_at_ms", nullable = false)
    private Long startedAtMs = 0L;

    @Builder.Default
    @Column(name = "finished_at_ms", nullable = false)
    private Long finishedAtMs = 0L;

    @Column(name = "expires_at_ms")
    private Long expiresAtMs;

    @Column(name = "deleted_at_ms")
    private Long deletedAtMs;

    @Builder.Default
    @Column(name = "start_lock_at_ms", nullable = false)
    private Long startLockAtMs = 0L;

    @Builder.Default
    @Column(name = "last_stop_attempt_at_ms", nullable = false)
    private Long lastStopAttemptAtMs = 0L;

    @Builder.Default
    @Column(name = "last_egress_sync_at_ms", nullable = false)
    private Long lastEgressSyncAtMs = 0L;

    @Builder.Default
    @Column(name = "egress_active_at_ms", nullable = false)
    private Long egressActiveAtMs = 0L;

    @Builder.Default
    @Column(name = "egress_ended_at_ms", nullable = false)
    private Long egressEndedAtMs = 0L;

    @Builder.Default
    @Column(name = "recorder_ready_at_ms", nullable = false)
    private Long recorderReadyAtMs = 0L;

    @Column(name = "recorder_ready_phase", length = 24)
    private String recorderReadyPhase;

    @Builder.Default
    @Column(name = "recorder_start_signal_at_ms", nullable = false)
    private Long recorderStartSignalAtMs = 0L;

    @Column(name = "recorder_start_phase", length = 24)
    private String recorderStartPhase;

    @Column(name = "recorder_livekit_identity", length = 220)
    private String recorderLivekitIdentity;

    @Builder.Default
    @Column(name = "duration_ns", nullable = false)
    private Long durationNs = 0L;

    @Builder.Default
    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes = 0L;

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "saved_by", nullable = false, columnDefinition = "jsonb")
    private List<String> savedBy = new ArrayList<>();

    @Builder.Default
    @Column(name = "saved_count", nullable = false)
    private Integer savedCount = 0;

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "shared_with", nullable = false, columnDefinition = "jsonb")
    private List<String> sharedWith = new ArrayList<>();

    @Builder.Default
    @Column(name = "shared_count", nullable = false)
    private Integer sharedCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean prewarmed = Boolean.FALSE;

    @Builder.Default
    @Column(name = "secret_word_recorded", nullable = false)
    private Boolean secretWordRecorded = Boolean.FALSE;

    @Column(name = "recording_view", length = 80)
    private String recordingView;

    @Column(name = "termination_reason", length = 60)
    private String terminationReason;

    @Column(name = "start_error", length = 600)
    private String startError;

    @Column(name = "egress_error", length = 600)
    private String egressError;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
