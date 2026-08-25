-- =====================================================================
-- MRS — Music Recommendation System
-- Flyway migration V1: initial schema
-- Source: MRS_ERD.drawio + MRS_RTW.xlsx (Data Dictionary, sheet 5)
-- MySQL 8.x | charset utf8mb4 | collation utf8mb4_0900_ai_ci
--   (default collation is case-insensitive -> satisfies DC-06
--    "email unique, case-insensitive" without extra handling)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. user  (reserved word in MySQL -> table name `users`)
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    username             VARCHAR(100) NOT NULL,
    email                VARCHAR(255) NOT NULL,
    password_hash        VARCHAR(255) NOT NULL,                -- BCrypt only (NFR-SEC02)
    role                 VARCHAR(20)  NOT NULL,                -- exactly one role (BR-01)
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    must_change_password BOOLEAN      NOT NULL DEFAULT TRUE,   -- FT-09: forced change on first login
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email),                         -- DC-06 (duplicate -> HTTP 409)
    CONSTRAINT ck_users_role   CHECK (role   IN ('ADMIN','CONTENT_DESIGNER','CUSTOMER')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','DEACTIVATED'))
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 2. song
-- ---------------------------------------------------------------------
CREATE TABLE song (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    title                 VARCHAR(255) NOT NULL,               -- import row w/o title skipped (SC-05)
    artist                VARCHAR(255) NULL,
    duration              INT          NULL,                   -- seconds, > 0 when present
    source_provider       VARCHAR(100) NOT NULL,               -- must be a registered provider (SC-05)
    external_source_id    VARCHAR(100) NULL,                   -- unique per provider (DC-04)
    spotify_popularity    TINYINT      NULL,                   -- 0-100 snapshot; NULL = not synced (BR-08)
    audio_url             VARCHAR(500) NULL,                   -- public HTTPS mp3 (vendor CDN or our bucket)
    cover_url             VARCHAR(500) NULL,
    ambience_a            VARCHAR(40)  NULL,                   -- wash colours sampled from the cover
    ambience_b            VARCHAR(40)  NULL,
    ambience_source_url   VARCHAR(500) NULL,                   -- cover those colours were taken from
    bpm                   INT          NULL,
    is_explicit           BOOLEAN      NULL,
    isrc                  VARCHAR(20)  NULL,
    source_etag           VARCHAR(64)  NULL,                   -- S3 object ETag last imported from
    version               INT          NOT NULL DEFAULT 0,     -- optimistic locking (DC-02, BR-06)
    PRIMARY KEY (id),
    UNIQUE KEY uq_song_provider_ext (source_provider, external_source_id),  -- import update-in-place (DC-04)
    KEY ix_song_popularity (spotify_popularity DESC),          -- default sort (FT-03/FT-05)
    CONSTRAINT ck_song_duration   CHECK (duration IS NULL OR duration > 0),
    CONSTRAINT ck_song_popularity CHECK (spotify_popularity IS NULL
                                         OR spotify_popularity BETWEEN 0 AND 100),
    CONSTRAINT ck_song_bpm        CHECK (bpm IS NULL OR bpm > 0)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 3. tag  (metadata: GENRE | MOOD | ARTIST | TAGS)
--    TAGS holds freeform provider descriptors (instruments, vibe words,
--    vocal style, etc.) used by LLM contextual search.
-- ---------------------------------------------------------------------
CREATE TABLE tag (
    id    BIGINT       NOT NULL AUTO_INCREMENT,
    type  VARCHAR(20)  NOT NULL,
    name  VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tag_type_name (type, name),                  -- unique per (type, name)
    CONSTRAINT ck_tag_type CHECK (type IN ('GENRE','MOOD','ARTIST','TAGS'))
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 4. song_tag  (M:N Song <-> Tag; song needs >= 1 tag to be filterable, DC-03)
-- ---------------------------------------------------------------------
CREATE TABLE song_tag (
    song_id BIGINT NOT NULL,
    tag_id  BIGINT NOT NULL,
    PRIMARY KEY (song_id, tag_id),
    KEY ix_songtag_tag (tag_id),                               -- filter entry point (tag -> songs)
    CONSTRAINT fk_songtag_song FOREIGN KEY (song_id) REFERENCES song (id) ON DELETE CASCADE,
    CONSTRAINT fk_songtag_tag  FOREIGN KEY (tag_id)  REFERENCES tag (id)  ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 5. playlist
-- ---------------------------------------------------------------------
CREATE TABLE playlist (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    name               VARCHAR(200) NOT NULL,                  -- rename only while Draft (DC-08)
    status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',  -- state machine SRS 4.4 (DC-01)
    owner_id           BIGINT       NOT NULL,                  -- reassign: ADMIN only (BR-14)
    created_by         BIGINT       NOT NULL,                  -- immutable, original creator
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_by   BIGINT       NOT NULL,
    published_at       DATETIME     NULL,                      -- set on publish, >= 1 song (BR-05)
    source_playlist_id BIGINT       NULL,                      -- clone lineage only (DC-07)
    version            INT          NOT NULL DEFAULT 1,        -- optimistic locking (DC-02, BR-06)
    PRIMARY KEY (id),
    KEY ix_playlist_owner  (owner_id),
    KEY ix_playlist_status (status),                           -- shared-workspace listing (FT-07)
    CONSTRAINT fk_playlist_owner      FOREIGN KEY (owner_id)           REFERENCES users (id),
    CONSTRAINT fk_playlist_createdby  FOREIGN KEY (created_by)         REFERENCES users (id),
    CONSTRAINT fk_playlist_modifiedby FOREIGN KEY (last_modified_by)   REFERENCES users (id),
    CONSTRAINT fk_playlist_source     FOREIGN KEY (source_playlist_id) REFERENCES playlist (id)
                                      ON DELETE SET NULL,      -- deleted source: lineage cleared, clone kept
    CONSTRAINT ck_playlist_status CHECK (status IN ('DRAFT','PUBLISHED'))
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 6. playlist_song  (ordered content; position contiguous 1..N per playlist)
-- ---------------------------------------------------------------------
CREATE TABLE playlist_song (
    playlist_id BIGINT NOT NULL,
    song_id     BIGINT NOT NULL,
    position    INT    NOT NULL,                               -- reorder renumbers atomically (else HTTP 422)
    PRIMARY KEY (playlist_id, song_id),
    UNIQUE KEY uq_playlistsong_position (playlist_id, position),
    CONSTRAINT fk_plsong_playlist FOREIGN KEY (playlist_id) REFERENCES playlist (id) ON DELETE CASCADE,
    CONSTRAINT fk_plsong_song     FOREIGN KEY (song_id)     REFERENCES song (id),
    CONSTRAINT ck_plsong_position CHECK (position >= 1)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 7. playlist_collaborator  (grants: owner/ADMIN -> Content Designers only, BR-03)
-- ---------------------------------------------------------------------
CREATE TABLE playlist_collaborator (
    playlist_id BIGINT   NOT NULL,
    user_id     BIGINT   NOT NULL,
    granted_by  BIGINT   NOT NULL,
    granted_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (playlist_id, user_id),                        -- unique grant (DC-09, duplicate -> HTTP 409)
    CONSTRAINT fk_plcollab_playlist FOREIGN KEY (playlist_id) REFERENCES playlist (id) ON DELETE CASCADE,
    CONSTRAINT fk_plcollab_user     FOREIGN KEY (user_id)     REFERENCES users (id),
    CONSTRAINT fk_plcollab_grantor  FOREIGN KEY (granted_by)  REFERENCES users (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 8. recommendation_log  (retention 12 months, scheduled purge only — SRS 4.3)
-- ---------------------------------------------------------------------
CREATE TABLE recommendation_log (
    id                  BIGINT   NOT NULL AUTO_INCREMENT,
    user_id             BIGINT   NOT NULL,
    query_text          TEXT     NULL,                         -- LLM queries 10-200 chars (API-level check)
    interpreted_filters JSON     NULL,
    llm_used            BOOLEAN  NOT NULL DEFAULT FALSE,       -- FT-04
    llm_succeeded       BOOLEAN  NULL,                         -- FT-04: fallback taken or not (BR-07)
    result_count        INT      NOT NULL DEFAULT 0,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY ix_reclog_user       (user_id),
    KEY ix_reclog_created_at (created_at),                     -- 12-month purge job
    CONSTRAINT fk_reclog_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 9. audit_log  (immutable; every admin/status-changing action — BR-10, NFR-SEC04)
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    actor_id    BIGINT      NOT NULL,
    action      VARCHAR(50) NOT NULL,                          -- USER_CREATE, ROLE_CHANGE, SONG_EDIT, ...
    entity_type VARCHAR(50) NOT NULL,
    entity_id   BIGINT      NOT NULL,
    timestamp   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details     JSON        NULL,                              -- before/after values (FT-09)
    PRIMARY KEY (id),
    KEY ix_auditlog_actor     (actor_id),
    KEY ix_auditlog_entity    (entity_type, entity_id),
    KEY ix_auditlog_timestamp (timestamp),                     -- 12-month purge job
    CONSTRAINT fk_auditlog_actor FOREIGN KEY (actor_id) REFERENCES users (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 10. catalog_import_run  (history of every S3/local catalog import, BR-10)
--     Not folded into audit_log: that table requires actor_id NOT NULL
--     with an FK to users, and a scheduled sync runs with no human actor.
--     A MANUAL run additionally writes audit_log with the real ADMIN.
--     trigger_type, not `trigger` — the latter is reserved in MySQL.
-- ---------------------------------------------------------------------
CREATE TABLE catalog_import_run (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    trigger_type   VARCHAR(20)  NOT NULL,                  -- STARTUP | SCHEDULED | MANUAL
    actor_id       BIGINT       NULL,                      -- NULL for unattended runs
    started_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at    DATETIME     NULL,                      -- NULL while in flight
    objects_listed INT          NOT NULL DEFAULT 0,
    added          INT          NOT NULL DEFAULT 0,
    updated        INT          NOT NULL DEFAULT 0,
    skipped        INT          NOT NULL DEFAULT 0,
    error          VARCHAR(500) NULL,                      -- set when the run failed
    PRIMARY KEY (id),
    KEY ix_importrun_started_at (started_at DESC),          -- "last run" lookup
    CONSTRAINT ck_importrun_trigger CHECK (trigger_type IN ('STARTUP','SCHEDULED','MANUAL')),
    CONSTRAINT fk_importrun_actor FOREIGN KEY (actor_id) REFERENCES users (id)
) ENGINE=InnoDB;
