package ru.hothat.model.room;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Комната. Скалярные поля, по которым идут выборки и игровая логика, вынесены
 * в колонки; свободные map/array-структуры документа Firestore остались JSONB —
 * у них нет фиксированной схемы и логика читает их целиком.
 */
@Entity
@Table(name = "room")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @Column(nullable = false, updatable = false, length = 80)
    private String id;

    @Column(length = 160)
    private String name;

    @Builder.Default
    @Column(nullable = false, length = 24)
    private String phase = "setup";

    @Column(name = "created_by", length = 160)
    private String createdBy;

    @Column(name = "previous_host_uid", length = 160)
    private String previousHostUid;

    @Column(name = "host_transferred_at")
    private Long hostTransferredAt;

    @Column(name = "host_transfer_type", length = 24)
    private String hostTransferType;

    @Column(name = "host_transferred_by", length = 160)
    private String hostTransferredBy;

    @Builder.Default
    @Column(name = "host_last_setup_activity_at", nullable = false)
    private Long hostLastSetupActivityAt = 0L;

    @Column(name = "host_last_setup_activity_kind", length = 40)
    private String hostLastSetupActivityKind;

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
    @Column(name = "team_lobby", nullable = false)
    private Boolean teamLobby = Boolean.FALSE;

    @Builder.Default
    @Column(name = "managed_matchmaking", nullable = false)
    private Boolean managedMatchmaking = Boolean.FALSE;

    @Column(name = "ranked_team_id", length = 80)
    private String rankedTeamId;

    @Builder.Default
    @Column(name = "division_language", nullable = false, length = 8)
    private String divisionLanguage = "ru";

    @Builder.Default
    @Column(name = "game_language", nullable = false, length = 8)
    private String gameLanguage = "ru";

    @Column(name = "matchmaking_language", length = 8)
    private String matchmakingLanguage;

    @Builder.Default
    @Column(name = "max_players", nullable = false)
    private Integer maxPlayers = 10;

    @Builder.Default
    @Column(name = "max_participants", nullable = false)
    private Integer maxParticipants = 10;

    @Column(name = "rating_target")
    private Integer ratingTarget;

    /** Сколько игроков видно в списке комнат: считается при обновлении присутствия. */
    @Builder.Default
    @Column(name = "public_active_players", nullable = false)
    private Integer publicActivePlayers = 0;

    @Builder.Default
    @Column(name = "public_presence_at", nullable = false)
    private Long publicPresenceAt = 0L;

    /** Чем раздаётся видео: сейчас всегда livekit, поле оставлено на будущее. */
    @Builder.Default
    @Column(name = "video_provider", length = 24)
    private String videoProvider = "livekit";

    @Builder.Default
    @Column(name = "game_number", nullable = false)
    private Integer gameNumber = 0;

    @Builder.Default
    @Column(name = "word_count", nullable = false)
    private Integer wordCount = 0;

    @Builder.Default
    @Column(name = "word_revision", nullable = false)
    private Integer wordRevision = 0;

    @Builder.Default
    @Column(name = "words_left", nullable = false)
    private Integer wordsLeft = 0;

    @Builder.Default
    @Column(name = "turn_duration", nullable = false)
    private Integer turnDuration = 60;

    @Column(name = "turn_duration_seconds")
    private Double turnDurationSeconds;

    @Column(name = "turn_started_at")
    private Instant turnStartedAt;

    @Builder.Default
    @Column(name = "turn_ends_at", nullable = false)
    private Long turnEndsAt = 0L;

    @Column(name = "turn_id", length = 80)
    private String turnId;

    @Column(name = "current_team_id", length = 80)
    private String currentTeamId;

    @Builder.Default
    @Column(name = "current_team_index", nullable = false)
    private Integer currentTeamIndex = 0;

    @Column(name = "current_word", length = 200)
    private String currentWord;

    @Builder.Default
    @Column(name = "current_turn_score", nullable = false)
    private Integer currentTurnScore = 0;

    @Column(name = "explainer_uid", length = 160)
    private String explainerUid;

    @Column(name = "explainer_name", length = 80)
    private String explainerName;

    @Column(name = "guesser_uid", length = 160)
    private String guesserUid;

    @Column(name = "guesser_name", length = 80)
    private String guesserName;

    @Column(name = "last_guessed_word", length = 200)
    private String lastGuessedWord;

    @Column(name = "last_skipped_word", length = 200)
    private String lastSkippedWord;

    @Column(name = "last_action_type", length = 40)
    private String lastActionType;

    @Column(name = "last_action_word", length = 200)
    private String lastActionWord;

    @Builder.Default
    @Column(name = "last_action_at_ms", nullable = false)
    private Long lastActionAtMs = 0L;

    @Builder.Default
    @Column(name = "appeal_ends_at", nullable = false)
    private Long appealEndsAt = 0L;

    @Builder.Default
    @Column(name = "game_paused", nullable = false)
    private Boolean gamePaused = Boolean.FALSE;

    @Builder.Default
    @Column(name = "host_paused", nullable = false)
    private Boolean hostPaused = Boolean.FALSE;

    @Column(name = "pause_reason", length = 40)
    private String pauseReason;

    @Builder.Default
    @Column(name = "pause_started_at_ms", nullable = false)
    private Long pauseStartedAtMs = 0L;

    @Builder.Default
    @Column(name = "paused_turn_remaining_ms", nullable = false)
    private Long pausedTurnRemainingMs = 0L;

    @Builder.Default
    @Column(name = "paused_appeal_remaining_ms", nullable = false)
    private Long pausedAppealRemainingMs = 0L;

    @Builder.Default
    @Column(name = "sabotage_cooldown_until", nullable = false)
    private Long sabotageCooldownUntil = 0L;

    @Builder.Default
    @Column(name = "record_game", nullable = false)
    private Boolean recordGame = Boolean.FALSE;

    @Column(name = "recording_preference_updated_at")
    private Instant recordingPreferenceUpdatedAt;

    @Column(name = "recording_preference_updated_by", length = 160)
    private String recordingPreferenceUpdatedBy;

    @Column(name = "matchmaking_started_at")
    private Long matchmakingStartedAt;

    @Builder.Default
    @Column(name = "matchmaking_deadline", nullable = false)
    private Long matchmakingDeadline = 0L;

    @Builder.Default
    @Column(name = "matchmaking_ready", nullable = false)
    private Boolean matchmakingReady = Boolean.FALSE;

    @Column(name = "matchmaking_min_players")
    private Integer matchmakingMinPlayers;

    @Builder.Default
    @Column(name = "matchmaking_expired", nullable = false)
    private Boolean matchmakingExpired = Boolean.FALSE;

    @Column(name = "test_owner_uid", length = 160)
    private String testOwnerUid;

    @Builder.Default
    @Column(name = "test_bot_next_action_at", nullable = false)
    private Long testBotNextActionAt = 0L;

    @Builder.Default
    @Column(name = "test_bot_next_sabotage_at", nullable = false)
    private Long testBotNextSabotageAt = 0L;

    @Builder.Default
    @Column(name = "test_bot_next_chat_at", nullable = false)
    private Long testBotNextChatAt = 0L;

    @Builder.Default
    @Column(name = "test_bot_special_effect_until", nullable = false)
    private Long testBotSpecialEffectUntil = 0L;

    @Builder.Default
    @Column(name = "test_bot_special_effect_cursor", nullable = false)
    private Integer testBotSpecialEffectCursor = 0;

    @Builder.Default
    @Column(name = "test_bot_voice_effect_until", nullable = false)
    private Long testBotVoiceEffectUntil = 0L;

    @Builder.Default
    @Column(name = "test_bot_voice_effect_cursor", nullable = false)
    private Integer testBotVoiceEffectCursor = 0;

    @Builder.Default
    @Column(name = "test_owner_explainer_game_number", nullable = false)
    private Integer testOwnerExplainerGameNumber = 0;

    @Builder.Default
    @Column(name = "test_owner_explainer_turn_number", nullable = false)
    private Integer testOwnerExplainerTurnNumber = 0;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by", length = 160)
    private String closedBy;

    @Column(name = "closed_reason", length = 200)
    private String closedReason;

    @Builder.Default
    @Column(name = "last_activity_at", nullable = false)
    private Long lastActivityAt = 0L;

    @Builder.Default
    @Type(JsonType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> bag = new ArrayList<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "team_order", nullable = false, columnDefinition = "jsonb")
    private List<String> teamOrder = new ArrayList<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "team_rosters", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> teamRosters = new HashMap<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "game_player_names_by_uid", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> gamePlayerNamesByUid = new HashMap<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "turn_guessed_words", nullable = false, columnDefinition = "jsonb")
    private List<Object> turnGuessedWords = new ArrayList<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "appeal_votes", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> appealVotes = new HashMap<>();

    @Type(JsonType.class)
    @Column(name = "last_turn", columnDefinition = "jsonb")
    private Map<String, Object> lastTurn;

    @Type(JsonType.class)
    @Column(name = "sabotage_event", columnDefinition = "jsonb")
    private Map<String, Object> sabotageEvent;

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "sabotage_events_recent", nullable = false, columnDefinition = "jsonb")
    private List<Object> sabotageEventsRecent = new ArrayList<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "sabotage_locks", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> sabotageLocks = new HashMap<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "replacement_recordings", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> replacementRecordings = new HashMap<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "special_reward_progress_by_team", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> specialRewardProgressByTeam = new HashMap<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "special_reward_cursor_by_team", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> specialRewardCursorByTeam = new HashMap<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "pause_missing_uids", nullable = false, columnDefinition = "jsonb")
    private List<String> pauseMissingUids = new ArrayList<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "pause_missing_names", nullable = false, columnDefinition = "jsonb")
    private List<String> pauseMissingNames = new ArrayList<>();

    @Type(JsonType.class)
    @Column(name = "technical_termination", columnDefinition = "jsonb")
    private Map<String, Object> technicalTermination;

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "test_bot_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> testBotIds = new ArrayList<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "test_bot_runtime", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> testBotRuntime = new HashMap<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "matchmaking_uids", nullable = false, columnDefinition = "jsonb")
    private List<String> matchmakingUids = new ArrayList<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "matchmaking_names", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> matchmakingNames = new HashMap<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "matchmaking_team_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> matchmakingTeamIds = new ArrayList<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "ranked_team_slots", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> rankedTeamSlots = new HashMap<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "ranked_team_assignments", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> rankedTeamAssignments = new HashMap<>();

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** Комната закрыта админом или матчмейкингом. */
    public boolean isClosed() {
        return closedAt != null || "closed".equals(phase);
    }

    public int effectiveMaxPlayers() {
        int value = maxPlayers == null ? 0 : maxPlayers;
        if (value <= 0) {
            value = maxParticipants == null ? 0 : maxParticipants;
        }
        return value <= 0 ? 10 : value;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
