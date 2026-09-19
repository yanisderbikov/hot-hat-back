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

/** rooms/{id}/players/{uid}. */
@Entity
@Table(name = "room_player")
@IdClass(RoomMemberId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomPlayer {

    @Id
    @Column(name = "room_id", nullable = false, length = 80)
    private String roomId;

    @Id
    @Column(nullable = false, length = 160)
    private String uid;

    @Column(length = 80)
    private String name;

    @Column(name = "avatar_data_url", columnDefinition = "text")
    private String avatarDataUrl;

    @Column(name = "team_id", length = 80)
    private String teamId;

    @Builder.Default
    @Column(name = "is_test_bot", nullable = false)
    private Boolean isTestBot = Boolean.FALSE;

    @Column(name = "test_bot_index")
    private Integer testBotIndex;

    @Builder.Default
    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    /**
     * Отметка сервера, по которой клиент вычисляет расхождение своих часов:
     * пишет serverTimestamp() и тут же читает обратно.
     */
    @Column(name = "clock_probe_at")
    private Instant clockProbeAt;

    @Builder.Default
    @Column(name = "last_seen_at", nullable = false)
    private Long lastSeenAt = 0L;

    @Builder.Default
    @Column(name = "media_revision", nullable = false)
    private Long mediaRevision = 0L;

    @Builder.Default
    @Column(name = "media_ready_at", nullable = false)
    private Long mediaReadyAt = 0L;

    @Builder.Default
    @Column(name = "camera_enabled", nullable = false)
    private Boolean cameraEnabled = Boolean.FALSE;

    @Builder.Default
    @Column(name = "microphone_enabled", nullable = false)
    private Boolean microphoneEnabled = Boolean.FALSE;

    /** Чем ведётся видеосвязь у этого игрока: клиент шлёт признак в heartbeat. */
    @Column(name = "video_provider", length = 24)
    private String videoProvider;

    @Builder.Default
    @Type(JsonType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> arsenal = new HashMap<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "meme_loadout", nullable = false, columnDefinition = "jsonb")
    private List<String> memeLoadout = new ArrayList<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "used_meme_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> usedMemeIds = new ArrayList<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "meme_available_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> memeAvailableIds = new ArrayList<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "meme_reserve_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> memeReserveIds = new ArrayList<>();

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "meme_recycle_queue", nullable = false, columnDefinition = "jsonb")
    private List<String> memeRecycleQueue = new ArrayList<>();

    @Builder.Default
    @Column(name = "meme_cycle_cursor", nullable = false)
    private Integer memeCycleCursor = 0;

    @Builder.Default
    @Column(name = "sabotage_cooldown_until", nullable = false)
    private Long sabotageCooldownUntil = 0L;

    @Column(name = "invited_by", length = 160)
    private String invitedBy;

    @Column(name = "invite_id", length = 120)
    private String inviteId;

    @Column(name = "promoted_by", length = 160)
    private String promotedBy;

    @Column(name = "banned_at")
    private Instant bannedAt;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /**
     * Тест-боты намеренно не пишут heartbeat, поэтому считаются активными всегда;
     * живые игроки — по lastSeenAt (playerDocIsActive из game.js).
     */
    public boolean isActive(long cutoff) {
        return Boolean.TRUE.equals(isTestBot) || (lastSeenAt != null && lastSeenAt >= cutoff);
    }
}
