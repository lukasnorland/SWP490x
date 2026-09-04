-- =====================================================================
-- MRS — Flyway migration V2: initial ADMIN account
-- Tags are created by the catalog import from provider metadata and by
-- curators on Add Song; catalog songs come from S3 import.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Initial ADMIN account
--    Password: Admin@2026  (meets BR-12: 8-64 chars, upper/digit/special)
--    must_change_password = TRUE -> forced change on first login (FT-09)
--    NOTE: for the real deployment, regenerate the hash and change the
--    password immediately. Never commit a production credential.
-- ---------------------------------------------------------------------
INSERT INTO users (username, email, password_hash, role, status, must_change_password) VALUES
('System Admin', 'admin@mrs.local',
 '$2a$10$S6Ja2VOI/uaySJ3zx834I.H4/f7hK/KGUZu/FzRatTKxAki9FLhQO',
 'ADMIN', 'ACTIVE', TRUE);
