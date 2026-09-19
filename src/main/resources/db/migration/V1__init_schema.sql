-- HOT-HAT: перенос Firestore-коллекций в PostgreSQL.
--
-- Идентификаторы документов сохранены как есть (uid Firebase Auth, hat-<hex16>
-- для комнат, rt-/inv-/ri- для команд и приглашений) — фронтенд оперирует
-- ровно этими строками, менять их при переезде нельзя.
--
-- Свободные map-поля документа (arsenal, sabotageLocks, testBotRuntime,
-- appealVotes, teamRosters) остаются JSONB: у них нет фиксированной схемы,
-- и игровая логика читает/пишет их целиком.

-- ───────────────────────────── users ─────────────────────────────
CREATE TABLE app_user (
    uid                          VARCHAR(160) PRIMARY KEY,
    email                        VARCHAR(320),
    nickname                     VARCHAR(40),
    display_name                 VARCHAR(80),
    avatar_data_url              TEXT,
    avatar_updated_at            TIMESTAMPTZ,
    division_language            VARCHAR(8)  NOT NULL DEFAULT 'ru',
    ui_language                  VARCHAR(8)  NOT NULL DEFAULT 'ru',
    division_locked_at           TIMESTAMPTZ,
    division_backfilled_at       TIMESTAMPTZ,
    division_explicitly_chosen_at TIMESTAMPTZ,
    ranked_team_id               VARCHAR(80),
    ranked_team_status           VARCHAR(24),
    sabotage_unlimited           BOOLEAN     NOT NULL DEFAULT FALSE,
    sabotage_games_used          INTEGER     NOT NULL DEFAULT 0,
    default_meme_loadout         JSONB       NOT NULL DEFAULT '[]'::jsonb,
    ranked_word_history          JSONB       NOT NULL DEFAULT '[]'::jsonb,
    ranked_word_history_updated_at TIMESTAMPTZ,
    last_seen_at                 BIGINT      NOT NULL DEFAULT 0,
    legal_accepted               BOOLEAN     NOT NULL DEFAULT FALSE,
    legal_versions               JSONB,
    legal_accepted_at            TIMESTAMPTZ,
    adult_confirmed              BOOLEAN     NOT NULL DEFAULT FALSE,
    registered_at                TIMESTAMPTZ,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_app_user_email ON app_user (lower(email));
CREATE INDEX idx_app_user_last_seen ON app_user (last_seen_at);
CREATE INDEX idx_app_user_registered ON app_user (registered_at);
CREATE INDEX idx_app_user_ranked_team ON app_user (ranked_team_id);

-- Уникальность ника: отдельная таблица вместо коллекции nicknames.
CREATE TABLE nickname_index (
    nickname_key VARCHAR(40)  PRIMARY KEY,
    uid          VARCHAR(160) NOT NULL,
    nickname     VARCHAR(40)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_nickname_index_uid ON nickname_index (uid);

CREATE TABLE user_ban (
    uid        VARCHAR(160) PRIMARY KEY,
    reason     VARCHAR(300),
    created_by VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE legal_consent (
    uid             VARCHAR(160) PRIMARY KEY,
    email           VARCHAR(320),
    adult_confirmed BOOLEAN     NOT NULL DEFAULT FALSE,
    versions        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    accepted_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    user_agent      VARCHAR(400)
);

CREATE TABLE support_request (
    id                 BIGSERIAL PRIMARY KEY,
    type               VARCHAR(40)  NOT NULL,
    uid                VARCHAR(160) NOT NULL,
    current_nickname   VARCHAR(40),
    requested_nickname VARCHAR(40),
    reason             VARCHAR(500),
    destination        VARCHAR(320),
    status             VARCHAR(24)  NOT NULL DEFAULT 'new',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ───────────────────────────── rooms ─────────────────────────────
CREATE TABLE room (
    id                         VARCHAR(80) PRIMARY KEY,
    name                       VARCHAR(160),
    phase                      VARCHAR(24) NOT NULL DEFAULT 'setup',
    created_by                 VARCHAR(160),
    previous_host_uid          VARCHAR(160),
    host_transferred_at        BIGINT,
    host_transfer_type         VARCHAR(24),
    host_transferred_by        VARCHAR(160),
    host_last_setup_activity_at      BIGINT NOT NULL DEFAULT 0,
    host_last_setup_activity_kind    VARCHAR(40),
    game_mode                  VARCHAR(24) NOT NULL DEFAULT 'classic',
    ranked                     BOOLEAN     NOT NULL DEFAULT FALSE,
    is_private                 BOOLEAN     NOT NULL DEFAULT FALSE,
    is_test_room               BOOLEAN     NOT NULL DEFAULT FALSE,
    team_lobby                 BOOLEAN     NOT NULL DEFAULT FALSE,
    managed_matchmaking        BOOLEAN     NOT NULL DEFAULT FALSE,
    ranked_team_id             VARCHAR(80),
    division_language          VARCHAR(8)  NOT NULL DEFAULT 'ru',
    game_language              VARCHAR(8)  NOT NULL DEFAULT 'ru',
    matchmaking_language       VARCHAR(8),
    max_players                INTEGER     NOT NULL DEFAULT 10,
    max_participants           INTEGER     NOT NULL DEFAULT 10,
    rating_target              INTEGER,
    game_number                INTEGER     NOT NULL DEFAULT 0,
    word_count                 INTEGER     NOT NULL DEFAULT 0,
    word_revision              INTEGER     NOT NULL DEFAULT 0,
    words_left                 INTEGER     NOT NULL DEFAULT 0,
    turn_duration              INTEGER     NOT NULL DEFAULT 60,
    turn_duration_seconds      DOUBLE PRECISION,
    turn_started_at            TIMESTAMPTZ,
    turn_ends_at               BIGINT      NOT NULL DEFAULT 0,
    turn_id                    VARCHAR(80),
    current_team_id            VARCHAR(80),
    current_team_index         INTEGER     NOT NULL DEFAULT 0,
    current_word               VARCHAR(200),
    current_turn_score         INTEGER     NOT NULL DEFAULT 0,
    explainer_uid              VARCHAR(160),
    explainer_name             VARCHAR(80),
    guesser_uid                VARCHAR(160),
    guesser_name               VARCHAR(80),
    last_guessed_word          VARCHAR(200),
    last_skipped_word          VARCHAR(200),
    last_action_type           VARCHAR(40),
    last_action_word           VARCHAR(200),
    last_action_at_ms          BIGINT      NOT NULL DEFAULT 0,
    appeal_ends_at             BIGINT      NOT NULL DEFAULT 0,
    game_paused                BOOLEAN     NOT NULL DEFAULT FALSE,
    host_paused                BOOLEAN     NOT NULL DEFAULT FALSE,
    pause_reason               VARCHAR(40),
    pause_started_at_ms        BIGINT      NOT NULL DEFAULT 0,
    paused_turn_remaining_ms   BIGINT      NOT NULL DEFAULT 0,
    paused_appeal_remaining_ms BIGINT      NOT NULL DEFAULT 0,
    sabotage_cooldown_until    BIGINT      NOT NULL DEFAULT 0,
    record_game                BOOLEAN     NOT NULL DEFAULT FALSE,
    recording_preference_updated_at TIMESTAMPTZ,
    recording_preference_updated_by VARCHAR(160),
    matchmaking_started_at     BIGINT,
    matchmaking_deadline       BIGINT      NOT NULL DEFAULT 0,
    matchmaking_ready          BOOLEAN     NOT NULL DEFAULT FALSE,
    matchmaking_min_players    INTEGER,
    matchmaking_expired        BOOLEAN     NOT NULL DEFAULT FALSE,
    test_owner_uid             VARCHAR(160),
    test_bot_next_action_at    BIGINT      NOT NULL DEFAULT 0,
    test_bot_next_sabotage_at  BIGINT      NOT NULL DEFAULT 0,
    test_bot_next_chat_at      BIGINT      NOT NULL DEFAULT 0,
    test_bot_special_effect_until  BIGINT  NOT NULL DEFAULT 0,
    test_bot_special_effect_cursor INTEGER NOT NULL DEFAULT 0,
    test_bot_voice_effect_until   BIGINT   NOT NULL DEFAULT 0,
    test_bot_voice_effect_cursor  INTEGER  NOT NULL DEFAULT 0,
    test_owner_explainer_game_number INTEGER NOT NULL DEFAULT 0,
    test_owner_explainer_turn_number INTEGER NOT NULL DEFAULT 0,
    closed_at                  TIMESTAMPTZ,
    closed_by                  VARCHAR(160),
    closed_reason              VARCHAR(200),
    last_activity_at           BIGINT      NOT NULL DEFAULT 0,
    -- Свободные структуры документа комнаты.
    bag                        JSONB       NOT NULL DEFAULT '[]'::jsonb,
    team_order                 JSONB       NOT NULL DEFAULT '[]'::jsonb,
    team_rosters               JSONB       NOT NULL DEFAULT '{}'::jsonb,
    game_player_names_by_uid   JSONB       NOT NULL DEFAULT '{}'::jsonb,
    turn_guessed_words         JSONB       NOT NULL DEFAULT '[]'::jsonb,
    appeal_votes               JSONB       NOT NULL DEFAULT '{}'::jsonb,
    last_turn                  JSONB,
    sabotage_event             JSONB,
    sabotage_events_recent     JSONB       NOT NULL DEFAULT '[]'::jsonb,
    sabotage_locks             JSONB       NOT NULL DEFAULT '{}'::jsonb,
    replacement_recordings     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    special_reward_progress_by_team JSONB  NOT NULL DEFAULT '{}'::jsonb,
    special_reward_cursor_by_team   JSONB  NOT NULL DEFAULT '{}'::jsonb,
    pause_missing_uids         JSONB       NOT NULL DEFAULT '[]'::jsonb,
    pause_missing_names        JSONB       NOT NULL DEFAULT '[]'::jsonb,
    technical_termination      JSONB,
    test_bot_ids               JSONB       NOT NULL DEFAULT '[]'::jsonb,
    test_bot_runtime           JSONB       NOT NULL DEFAULT '{}'::jsonb,
    matchmaking_uids           JSONB       NOT NULL DEFAULT '[]'::jsonb,
    matchmaking_names          JSONB       NOT NULL DEFAULT '{}'::jsonb,
    matchmaking_team_ids       JSONB       NOT NULL DEFAULT '[]'::jsonb,
    ranked_team_slots          JSONB       NOT NULL DEFAULT '{}'::jsonb,
    ranked_team_assignments    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_room_phase ON room (phase);
CREATE INDEX idx_room_updated ON room (updated_at DESC);
CREATE INDEX idx_room_managed ON room (managed_matchmaking, phase);

CREATE TABLE room_player (
    room_id                VARCHAR(80)  NOT NULL REFERENCES room (id) ON DELETE CASCADE,
    uid                    VARCHAR(160) NOT NULL,
    name                   VARCHAR(80),
    avatar_data_url        TEXT,
    team_id                VARCHAR(80),
    is_test_bot            BOOLEAN     NOT NULL DEFAULT FALSE,
    test_bot_index         INTEGER,
    joined_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at           BIGINT      NOT NULL DEFAULT 0,
    media_revision         BIGINT      NOT NULL DEFAULT 0,
    media_ready_at         BIGINT      NOT NULL DEFAULT 0,
    camera_enabled         BOOLEAN     NOT NULL DEFAULT FALSE,
    microphone_enabled     BOOLEAN     NOT NULL DEFAULT FALSE,
    arsenal                JSONB       NOT NULL DEFAULT '{}'::jsonb,
    meme_loadout           JSONB       NOT NULL DEFAULT '[]'::jsonb,
    used_meme_ids          JSONB       NOT NULL DEFAULT '[]'::jsonb,
    meme_available_ids     JSONB       NOT NULL DEFAULT '[]'::jsonb,
    meme_reserve_ids       JSONB       NOT NULL DEFAULT '[]'::jsonb,
    meme_recycle_queue     JSONB       NOT NULL DEFAULT '[]'::jsonb,
    meme_cycle_cursor      INTEGER     NOT NULL DEFAULT 0,
    sabotage_cooldown_until BIGINT     NOT NULL DEFAULT 0,
    invited_by             VARCHAR(160),
    invite_id              VARCHAR(120),
    promoted_by            VARCHAR(160),
    banned_at              TIMESTAMPTZ,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (room_id, uid)
);
CREATE INDEX idx_room_player_uid ON room_player (uid);

CREATE TABLE room_spectator (
    room_id         VARCHAR(80)  NOT NULL REFERENCES room (id) ON DELETE CASCADE,
    uid             VARCHAR(160) NOT NULL,
    name            VARCHAR(80),
    avatar_data_url TEXT,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at    BIGINT      NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (room_id, uid)
);

CREATE TABLE room_team (
    room_id        VARCHAR(80) NOT NULL REFERENCES room (id) ON DELETE CASCADE,
    team_id        VARCHAR(80) NOT NULL,
    name           VARCHAR(120),
    team_order     INTEGER     NOT NULL DEFAULT 0,
    score          INTEGER     NOT NULL DEFAULT 0,
    member_uids    JSONB       NOT NULL DEFAULT '[]'::jsonb,
    ranked_team_id VARCHAR(80),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (room_id, team_id)
);

CREATE TABLE room_chat_message (
    id            VARCHAR(120) NOT NULL,
    room_id       VARCHAR(80)  NOT NULL REFERENCES room (id) ON DELETE CASCADE,
    uid           VARCHAR(160),
    name          VARCHAR(80),
    role          VARCHAR(24),
    text          VARCHAR(800),
    is_test_bot   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at_ms BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (room_id, id)
);
CREATE INDEX idx_room_chat_created ON room_chat_message (room_id, created_at_ms);

CREATE TABLE room_word_submission (
    room_id                VARCHAR(80)  NOT NULL REFERENCES room (id) ON DELETE CASCADE,
    submission_id          VARCHAR(120) NOT NULL,
    words                  JSONB       NOT NULL DEFAULT '[]'::jsonb,
    word_count             INTEGER     NOT NULL DEFAULT 0,
    is_test_bot_submission BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (room_id, submission_id)
);

-- ─────────────────────── рейтинговые команды ───────────────────────
CREATE TABLE ranked_team (
    id                VARCHAR(80) PRIMARY KEY,
    name              VARCHAR(60)  NOT NULL,
    owner_uid         VARCHAR(160) NOT NULL,
    member_uids       JSONB        NOT NULL DEFAULT '[]'::jsonb,
    member_nicknames  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    division_language VARCHAR(8)   NOT NULL DEFAULT 'ru',
    logo_data_url     TEXT,
    status            VARCHAR(24)  NOT NULL DEFAULT 'pending',
    rating            JSONB        NOT NULL DEFAULT '{}'::jsonb,
    confirmed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE ranked_team_name (
    name_key VARCHAR(60) PRIMARY KEY,
    team_id  VARCHAR(80) NOT NULL,
    name     VARCHAR(60) NOT NULL
);

CREATE TABLE team_invite (
    id                VARCHAR(80) PRIMARY KEY,
    team_id           VARCHAR(80)  NOT NULL,
    team_name         VARCHAR(60)  NOT NULL,
    owner_uid         VARCHAR(160) NOT NULL,
    owner_nickname    VARCHAR(40),
    invitee_uid       VARCHAR(160) NOT NULL,
    division_language VARCHAR(8)   NOT NULL DEFAULT 'ru',
    status            VARCHAR(24)  NOT NULL DEFAULT 'pending',
    answered_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_team_invite_invitee ON team_invite (invitee_uid, status);

CREATE TABLE ranked_team_preflight (
    team_id        VARCHAR(80) PRIMARY KEY,
    member_uids    JSONB       NOT NULL DEFAULT '[]'::jsonb,
    intent         VARCHAR(16) NOT NULL DEFAULT 'quick',
    room_id        VARCHAR(80),
    game_mode      VARCHAR(24) NOT NULL DEFAULT 'classic',
    initiator_uid  VARCHAR(160),
    ready          JSONB       NOT NULL DEFAULT '{}'::jsonb,
    media          JSONB       NOT NULL DEFAULT '{}'::jsonb,
    media_at       JSONB       NOT NULL DEFAULT '{}'::jsonb,
    started_at     BIGINT      NOT NULL DEFAULT 0,
    updated_at     BIGINT      NOT NULL DEFAULT 0,
    expires_at     BIGINT      NOT NULL DEFAULT 0,
    target_room_id VARCHAR(80),
    room_ready     BOOLEAN     NOT NULL DEFAULT FALSE,
    search_started BOOLEAN     NOT NULL DEFAULT FALSE,
    search_count   INTEGER     NOT NULL DEFAULT 0,
    failed         BOOLEAN     NOT NULL DEFAULT FALSE
);

-- ───────────────────────── сезонные рейтинги ─────────────────────────
CREATE TABLE season_ranking (
    id       VARCHAR(80) PRIMARY KEY,
    champion JSONB
);

CREATE TABLE season_ranking_team (
    ranking_id             VARCHAR(80) NOT NULL,
    team_id                VARCHAR(80) NOT NULL,
    name                   VARCHAR(60),
    logo_data_url          TEXT,
    division_language      VARCHAR(8)  NOT NULL DEFAULT 'ru',
    member_uids            JSONB       NOT NULL DEFAULT '[]'::jsonb,
    member_nicknames       JSONB       NOT NULL DEFAULT '[]'::jsonb,
    points                 INTEGER     NOT NULL DEFAULT 0,
    games                  INTEGER     NOT NULL DEFAULT 0,
    wins                   INTEGER     NOT NULL DEFAULT 0,
    technical_forfeits     INTEGER     NOT NULL DEFAULT 0,
    last_technical_penalty INTEGER     NOT NULL DEFAULT 0,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (ranking_id, team_id)
);
CREATE INDEX idx_season_team_points ON season_ranking_team (ranking_id, points DESC);

CREATE TABLE season_ranking_player (
    ranking_id        VARCHAR(80)  NOT NULL,
    uid               VARCHAR(160) NOT NULL,
    nickname          VARCHAR(40),
    avatar_data_url   TEXT,
    division_language VARCHAR(8)   NOT NULL DEFAULT 'ru',
    team_id           VARCHAR(80),
    team_name         VARCHAR(60),
    points            INTEGER      NOT NULL DEFAULT 0,
    last_team_points  INTEGER      NOT NULL DEFAULT 0,
    games             INTEGER      NOT NULL DEFAULT 0,
    wins              INTEGER      NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (ranking_id, uid)
);
CREATE INDEX idx_season_player_points ON season_ranking_player (ranking_id, points DESC);

CREATE TABLE ranked_result_event (
    game_key          VARCHAR(120) PRIMARY KEY,
    room_id           VARCHAR(80),
    game_number       INTEGER     NOT NULL DEFAULT 0,
    ranking_id        VARCHAR(80),
    division_language VARCHAR(8),
    technical         BOOLEAN     NOT NULL DEFAULT FALSE,
    annulled          BOOLEAN     NOT NULL DEFAULT FALSE,
    reason            VARCHAR(60),
    culprit_team_ids  JSONB       NOT NULL DEFAULT '[]'::jsonb,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sabotage_game_use (
    id          VARCHAR(200) PRIMARY KEY,
    uid         VARCHAR(160) NOT NULL,
    room_id     VARCHAR(80),
    game_number INTEGER     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ────────────────────────── соцчасть ──────────────────────────
CREATE TABLE friend_link (
    pair        VARCHAR(340) PRIMARY KEY,
    member_uids JSONB       NOT NULL DEFAULT '[]'::jsonb,
    uid_a       VARCHAR(160) NOT NULL,
    uid_b       VARCHAR(160) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_friend_link_a ON friend_link (uid_a);
CREATE INDEX idx_friend_link_b ON friend_link (uid_b);

CREATE TABLE friend_request (
    id            BIGSERIAL PRIMARY KEY,
    pair          VARCHAR(340) NOT NULL,
    from_uid      VARCHAR(160) NOT NULL,
    from_nickname VARCHAR(40),
    to_uid        VARCHAR(160) NOT NULL,
    to_nickname   VARCHAR(40),
    status        VARCHAR(24)  NOT NULL DEFAULT 'pending',
    answered_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_friend_request_to ON friend_request (to_uid, status);
CREATE INDEX idx_friend_request_from ON friend_request (from_uid);
CREATE INDEX idx_friend_request_pair ON friend_request (pair, status);

CREATE TABLE direct_chat (
    pair               VARCHAR(340) PRIMARY KEY,
    member_uids        JSONB       NOT NULL DEFAULT '[]'::jsonb,
    member_nicknames   JSONB       NOT NULL DEFAULT '{}'::jsonb,
    member_avatars     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    last_text          VARCHAR(200),
    last_from_uid      VARCHAR(160),
    last_from_nickname VARCHAR(40),
    last_at            TIMESTAMPTZ,
    unread_for         JSONB       NOT NULL DEFAULT '[]'::jsonb,
    unread_counts      JSONB       NOT NULL DEFAULT '{}'::jsonb,
    last_read_at_ms    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE direct_chat_message (
    id                   BIGSERIAL PRIMARY KEY,
    pair                 VARCHAR(340) NOT NULL REFERENCES direct_chat (pair) ON DELETE CASCADE,
    type                 VARCHAR(40),
    from_uid             VARCHAR(160) NOT NULL,
    from_nickname        VARCHAR(40),
    to_uid               VARCHAR(160) NOT NULL,
    member_uids          JSONB        NOT NULL DEFAULT '[]'::jsonb,
    text                 VARCHAR(800),
    attachment           JSONB,
    room_invite_id       VARCHAR(120),
    room_id              VARCHAR(80),
    room_name            VARCHAR(160),
    room_invite_game_number INTEGER,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at_ms        BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_direct_msg_pair ON direct_chat_message (pair, created_at DESC);

CREATE TABLE social_inbox (
    uid                       VARCHAR(160) PRIMARY KEY,
    message_version           BIGINT      NOT NULL DEFAULT 0,
    unread_messages           INTEGER     NOT NULL DEFAULT 0,
    last_message_at_ms        BIGINT      NOT NULL DEFAULT 0,
    last_message_from_uid     VARCHAR(160),
    last_message_from_nickname VARCHAR(40),
    last_message_text         VARCHAR(200),
    room_invite_version       BIGINT      NOT NULL DEFAULT 0,
    room_invite_id            VARCHAR(120),
    room_invite_room_id       VARCHAR(80),
    room_invite_room_name     VARCHAR(160),
    room_invite_from_uid      VARCHAR(160),
    room_invite_from_nickname VARCHAR(40),
    room_invite_status        VARCHAR(24),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE room_invite (
    id                   VARCHAR(120) PRIMARY KEY,
    room_id              VARCHAR(80)  NOT NULL,
    room_name            VARCHAR(160),
    from_uid             VARCHAR(160) NOT NULL,
    from_nickname        VARCHAR(40),
    to_uid               VARCHAR(160) NOT NULL,
    status               VARCHAR(24)  NOT NULL DEFAULT 'pending',
    game_number_at_invite INTEGER     NOT NULL DEFAULT 0,
    chat_id              VARCHAR(340),
    message_id           VARCHAR(80),
    accepted_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at_ms        BIGINT       NOT NULL DEFAULT 0,
    expires_at_ms        BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_room_invite_to ON room_invite (to_uid, status);

-- ─────────────────────── мемы и записи игр ───────────────────────
CREATE TABLE meme_library (
    id                VARCHAR(180) PRIMARY KEY,
    title             VARCHAR(120),
    duration_ms       INTEGER     NOT NULL DEFAULT 5000,
    src               VARCHAR(2200),
    poster            VARCHAR(2200),
    data_url          TEXT,
    mime              VARCHAR(100),
    media_path        VARCHAR(500),
    poster_path       VARCHAR(500),
    storage_provider  VARCHAR(40),
    media_version     INTEGER     NOT NULL DEFAULT 0,
    byte_size         BIGINT      NOT NULL DEFAULT 0,
    owner_uid         VARCHAR(160),
    owner_name        VARCHAR(80),
    division_language VARCHAR(8)  NOT NULL DEFAULT 'ru',
    status            VARCHAR(24) NOT NULL DEFAULT 'active',
    builtin           BOOLEAN     NOT NULL DEFAULT FALSE,
    optimized_version VARCHAR(40),
    optimized_at      TIMESTAMPTZ,
    migrated_to_s3_at TIMESTAMPTZ,
    migrated_to_vps_at BIGINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_meme_status ON meme_library (status);

CREATE TABLE game_recording (
    id                     VARCHAR(180) PRIMARY KEY,
    room_id                VARCHAR(80),
    game_number            INTEGER     NOT NULL DEFAULT 0,
    room_name              VARCHAR(160),
    game_mode              VARCHAR(24) NOT NULL DEFAULT 'classic',
    ranked                 BOOLEAN     NOT NULL DEFAULT FALSE,
    is_private             BOOLEAN     NOT NULL DEFAULT FALSE,
    is_test_room           BOOLEAN     NOT NULL DEFAULT FALSE,
    division_language      VARCHAR(8)  NOT NULL DEFAULT 'ru',
    game_language          VARCHAR(8)  NOT NULL DEFAULT 'ru',
    participants           JSONB       NOT NULL DEFAULT '[]'::jsonb,
    participant_uids       JSONB       NOT NULL DEFAULT '[]'::jsonb,
    teams                  JSONB       NOT NULL DEFAULT '[]'::jsonb,
    winner_team_ids        JSONB       NOT NULL DEFAULT '[]'::jsonb,
    winner_team_names      JSONB       NOT NULL DEFAULT '[]'::jsonb,
    winning_score          INTEGER     NOT NULL DEFAULT 0,
    total_score            INTEGER     NOT NULL DEFAULT 0,
    word_count             INTEGER     NOT NULL DEFAULT 0,
    status                 VARCHAR(24) NOT NULL DEFAULT 'starting',
    egress_id              VARCHAR(120),
    livekit_status         INTEGER,
    object_path            VARCHAR(500),
    storage_provider       VARCHAR(40),
    storage_bucket         VARCHAR(120),
    started_at_ms          BIGINT      NOT NULL DEFAULT 0,
    finished_at_ms         BIGINT      NOT NULL DEFAULT 0,
    expires_at_ms          BIGINT,
    deleted_at_ms          BIGINT,
    start_lock_at_ms       BIGINT      NOT NULL DEFAULT 0,
    last_stop_attempt_at_ms BIGINT     NOT NULL DEFAULT 0,
    last_egress_sync_at_ms BIGINT      NOT NULL DEFAULT 0,
    egress_active_at_ms    BIGINT      NOT NULL DEFAULT 0,
    egress_ended_at_ms     BIGINT      NOT NULL DEFAULT 0,
    recorder_ready_at_ms   BIGINT      NOT NULL DEFAULT 0,
    recorder_ready_phase   VARCHAR(24),
    recorder_start_signal_at_ms BIGINT NOT NULL DEFAULT 0,
    recorder_start_phase   VARCHAR(24),
    recorder_livekit_identity VARCHAR(220),
    duration_ns            BIGINT      NOT NULL DEFAULT 0,
    size_bytes             BIGINT      NOT NULL DEFAULT 0,
    saved_by               JSONB       NOT NULL DEFAULT '[]'::jsonb,
    saved_count            INTEGER     NOT NULL DEFAULT 0,
    shared_with            JSONB       NOT NULL DEFAULT '[]'::jsonb,
    shared_count           INTEGER     NOT NULL DEFAULT 0,
    prewarmed              BOOLEAN     NOT NULL DEFAULT FALSE,
    secret_word_recorded   BOOLEAN     NOT NULL DEFAULT FALSE,
    recording_view         VARCHAR(80),
    termination_reason     VARCHAR(60),
    start_error            VARCHAR(600),
    egress_error           VARCHAR(600),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_recording_started ON game_recording (started_at_ms DESC);
CREATE INDEX idx_recording_expires ON game_recording (expires_at_ms);
CREATE INDEX idx_recording_room ON game_recording (room_id, game_number);

-- ─────────────────────── аналитика и мониторинг ───────────────────────
CREATE TABLE analytics_event (
    event_key  VARCHAR(160) PRIMARY KEY,
    event_type VARCHAR(40)  NOT NULL,
    uid        VARCHAR(160),
    email      VARCHAR(320),
    room_id    VARCHAR(80),
    payload    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_analytics_created ON analytics_event (created_at);
CREATE INDEX idx_analytics_type ON analytics_event (event_type, created_at);

CREATE TABLE usage_daily (
    date       VARCHAR(10) PRIMARY KEY,
    latest     JSONB,
    vps_network_tx_bytes BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE usage_monthly (
    month      VARCHAR(7) PRIMARY KEY,
    vps_network_tx_bytes BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE usage_alert (
    id         VARCHAR(200) PRIMARY KEY,
    metric     JSONB,
    threshold  INTEGER     NOT NULL DEFAULT 50,
    status     VARCHAR(24) NOT NULL DEFAULT 'pending',
    email      JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);
CREATE INDEX idx_usage_alert_created ON usage_alert (created_at DESC);

CREATE TABLE usage_report (
    date       VARCHAR(10) PRIMARY KEY,
    time_zone  VARCHAR(60),
    status     VARCHAR(24) NOT NULL DEFAULT 'pending',
    snapshot   JSONB,
    email      JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);

CREATE TABLE usage_mail_counter (
    period_key VARCHAR(16) PRIMARY KEY,
    sent       INTEGER     NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE usage_monitor_state (
    id         VARCHAR(60) PRIMARY KEY,
    tx_bytes   BIGINT,
    rx_bytes   BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE maintenance_state (
    id          VARCHAR(60) PRIMARY KEY,
    last_run_at BIGINT      NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
