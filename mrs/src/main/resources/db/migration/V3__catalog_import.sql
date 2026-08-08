-- =====================================================================
-- MRS — Flyway migration V3: catalog import from S3
-- Covers: provider metadata the staged JSON carries but V1 had no home
--         for, the ETag used to detect new/changed objects, and the
--         run history of each import (UC-28 / P-06c, flow F-05).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. song — provider metadata + import bookkeeping
--    preview_url: the provider's public CDN mp3. Distinct from
--    audio_s3_key, which stays reserved for licensed audio held in our
--    own bucket and exported via pre-signed URLs (BR-13).
--    source_etag: hash of the S3 object this row was last built from.
--    A sync compares it against the ETag returned by ListObjectsV2 and
--    only downloads objects that are new or whose content changed, so a
--    poll over an unchanged prefix reads no object bodies at all.
-- ---------------------------------------------------------------------
ALTER TABLE song
    ADD COLUMN preview_url  VARCHAR(500) NULL AFTER audio_s3_key,
    ADD COLUMN cover_url    VARCHAR(500) NULL AFTER preview_url,
    ADD COLUMN bpm          INT          NULL AFTER cover_url,
    ADD COLUMN energy_level VARCHAR(20)  NULL AFTER bpm,
    ADD COLUMN has_vocals   BOOLEAN      NULL AFTER energy_level,
    ADD COLUMN is_explicit  BOOLEAN      NULL AFTER has_vocals,
    ADD COLUMN isrc         VARCHAR(20)  NULL AFTER is_explicit,
    ADD COLUMN source_etag  VARCHAR(64)  NULL AFTER isrc,
    ADD CONSTRAINT ck_song_bpm CHECK (bpm IS NULL OR bpm > 0);

-- The 20 DemoProvider seed songs of V2 have no object in S3, so they are
-- never listed by a sync and keep source_etag NULL for good. Their rows
-- are left untouched: a sync only ever touches what S3 actually holds.

-- ---------------------------------------------------------------------
-- 2. catalog_import_run  (history of every import, BR-10)
--    Not folded into audit_log: that table requires actor_id NOT NULL
--    with an FK to users, and a scheduled sync runs with no human actor.
--    A MANUAL run additionally writes audit_log with the real ADMIN.
--    trigger_type, not `trigger` — the latter is reserved in MySQL.
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
