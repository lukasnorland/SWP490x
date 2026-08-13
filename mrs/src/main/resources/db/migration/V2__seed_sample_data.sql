-- =====================================================================
-- MRS — Flyway migration V2: seed data (dev/demo)
-- Covers: initial ADMIN account, starter tag vocabulary (4 types).
-- Catalog songs come from S3 import (EpidemicSound / NCS / OneOff).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Initial ADMIN account
--    Password: Admin@2026  (meets BR-12: 8-64 chars, upper/digit/special)
--    must_change_password = TRUE -> forced change on first login (FT-09)
--    NOTE: for the real deployment, regenerate the hash and change the
--    password immediately. Never commit a production credential.
-- ---------------------------------------------------------------------
INSERT INTO users (username, email, password_hash, role, status, must_change_password) VALUES
('System Admin', 'admin@mrs.local',
 '$2a$10$S6Ja2VOI/uaySJ3zx834I.H4/f7hK/KGUZu/FzRatTKxAki9FLhQO',
 'ADMIN', 'ACTIVE', TRUE);

-- Dev-only extra accounts (same password) — remove for production
INSERT INTO users (username, email, password_hash, role, status, must_change_password) VALUES
('Demo Content Designer', 'cd@mrs.local',
 '$2a$10$S6Ja2VOI/uaySJ3zx834I.H4/f7hK/KGUZu/FzRatTKxAki9FLhQO',
 'CONTENT_DESIGNER', 'ACTIVE', TRUE),
('Demo Customer', 'customer@mrs.local',
 '$2a$10$S6Ja2VOI/uaySJ3zx834I.H4/f7hK/KGUZu/FzRatTKxAki9FLhQO',
 'CUSTOMER', 'ACTIVE', TRUE);

-- ---------------------------------------------------------------------
-- 2. Starter tag vocabulary (GENRE | MOOD | ARTIST | TAGS)
--    Import creates additional tags from provider metadata as needed.
-- ---------------------------------------------------------------------
INSERT INTO tag (type, name) VALUES
-- GENRE
('GENRE','Pop'), ('GENRE','EDM'), ('GENRE','Acoustic'), ('GENRE','Piano'),
('GENRE','Rock'), ('GENRE','Lo-fi'), ('GENRE','Orchestral'),
-- MOOD
('MOOD','Energetic'), ('MOOD','Cheerful'), ('MOOD','Emotional'),
('MOOD','Relaxing'), ('MOOD','Dramatic'), ('MOOD','Dreamy'),
-- ARTIST (tag-level artist grouping used by search; free-text artist stays on song)
('ARTIST','Various Indie'), ('ARTIST','Studio Session'),
-- TAGS (freeform provider descriptors for LLM / subtle filters)
('TAGS','driving'), ('TAGS','upbeat'), ('TAGS','hopeful'), ('TAGS','romantic'),
('TAGS','ballad'), ('TAGS','uplifting'), ('TAGS','love'), ('TAGS','guitar'),
('TAGS','piano'), ('TAGS','feelgood'), ('TAGS','cozy'), ('TAGS','powerful');
