-- =====================================================================
-- MRS — Flyway migration V2: seed data (dev/demo)
-- Covers: initial ADMIN account, tag vocabulary (5 types),
--         sample catalog for the W3 vertical prototype (search API)
-- Also mitigates Risk #4 (Report 2): normalized sample dataset, W2-3
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
-- 2. Tag vocabulary (GENRE | MOOD | THEME | ARTIST | EVENT)
-- ---------------------------------------------------------------------
INSERT INTO tag (type, name) VALUES
-- GENRE
('GENRE','Pop'), ('GENRE','EDM'), ('GENRE','Acoustic'), ('GENRE','Piano'),
('GENRE','Rock'), ('GENRE','Lo-fi'), ('GENRE','Orchestral'),
-- MOOD
('MOOD','Energetic'), ('MOOD','Cheerful'), ('MOOD','Emotional'),
('MOOD','Relaxing'), ('MOOD','Dramatic'), ('MOOD','Dreamy'),
-- THEME
('THEME','Celebration'), ('THEME','Love'), ('THEME','Adventure'),
('THEME','Background'), ('THEME','Party'),
-- ARTIST (tag-level artist grouping used by search; free-text artist stays on song)
('ARTIST','Various Indie'), ('ARTIST','Studio Session'),
-- EVENT
('EVENT','New Year'), ('EVENT','Christmas'), ('EVENT','Summer'),
('EVENT','Holiday'), ('EVENT','Halloween');

-- ---------------------------------------------------------------------
-- 3. Sample songs (registered provider: 'DemoProvider')
--    spotify_popularity: 0-100 snapshot; one NULL case to test BR-08
--    (unscored songs always sort last, never hidden)
-- ---------------------------------------------------------------------
INSERT INTO song (title, artist, duration, source_provider, external_source_id,
                  spotify_popularity, popularity_synced_at, audio_s3_key, version) VALUES
('Firework Nights',       'Nova Bloom',      212, 'DemoProvider', 'DP-0001', 88, NOW(), 'audio/demo/dp-0001.mp3', 0),
('Countdown Lights',      'The Meridians',   198, 'DemoProvider', 'DP-0002', 81, NOW(), 'audio/demo/dp-0002.mp3', 0),
('Snowfall Waltz',        'Elena Frost',     241, 'DemoProvider', 'DP-0003', 76, NOW(), 'audio/demo/dp-0003.mp3', 0),
('Sleigh Ride Groove',    'Jolly Union',     185, 'DemoProvider', 'DP-0004', 72, NOW(), 'audio/demo/dp-0004.mp3', 0),
('Beach Day Anthem',      'Sunset Riders',   205, 'DemoProvider', 'DP-0005', 90, NOW(), 'audio/demo/dp-0005.mp3', 0),
('Tropical Rush',         'Nova Bloom',      190, 'DemoProvider', 'DP-0006', 68, NOW(), 'audio/demo/dp-0006.mp3', 0),
('Quiet Snow Piano',      'Elena Frost',     263, 'DemoProvider', 'DP-0007', 64, NOW(), 'audio/demo/dp-0007.mp3', 0),
('Midnight Confetti',     'The Meridians',   221, 'DemoProvider', 'DP-0008', 79, NOW(), 'audio/demo/dp-0008.mp3', 0),
('Lazy Sunday Lo-fi',     'Studio Cats',     176, 'DemoProvider', 'DP-0009', 83, NOW(), 'audio/demo/dp-0009.mp3', 0),
('Hero''s Horizon',       'Grand Ensemble',  312, 'DemoProvider', 'DP-0010', 58, NOW(), 'audio/demo/dp-0010.mp3', 0),
('Summer Sparks',         'Sunset Riders',   201, 'DemoProvider', 'DP-0011', 74, NOW(), 'audio/demo/dp-0011.mp3', 0),
('First Dance',           'Elena Frost',     234, 'DemoProvider', 'DP-0012', 70, NOW(), 'audio/demo/dp-0012.mp3', 0),
('Pumpkin Parade',        'Jolly Union',     193, 'DemoProvider', 'DP-0013', 55, NOW(), 'audio/demo/dp-0013.mp3', 0),
('Soft Focus',            'Studio Cats',     168, 'DemoProvider', 'DP-0014', 66, NOW(), 'audio/demo/dp-0014.mp3', 0),
('Rooftop Party',         'The Meridians',   210, 'DemoProvider', 'DP-0015', 85, NOW(), 'audio/demo/dp-0015.mp3', 0),
('Winter Embers',         'Grand Ensemble',  287, 'DemoProvider', 'DP-0016', 61, NOW(), 'audio/demo/dp-0016.mp3', 0),
('Neon Tide',             'Nova Bloom',      199, 'DemoProvider', 'DP-0017', 77, NOW(), 'audio/demo/dp-0017.mp3', 0),
('Gentle Morning',        'Studio Cats',     182, 'DemoProvider', 'DP-0018', 69, NOW(), NULL,                    0),  -- no audio yet -> blank CSV URL cell (BR-13)
('Carol of Strings',      'Grand Ensemble',  256, 'DemoProvider', 'DP-0019', NULL, NULL, 'audio/demo/dp-0019.mp3', 0), -- unscored -> sorts last (BR-08)
('New Dawn Fanfare',      'Grand Ensemble',  244, 'DemoProvider', 'DP-0020', 62, NOW(), 'audio/demo/dp-0020.mp3', 0);

-- ---------------------------------------------------------------------
-- 4. Song-tag links (every song >= 1 tag so it is filterable, DC-03)
--    Lookups by natural key -> script stays valid regardless of ids
-- ---------------------------------------------------------------------
INSERT INTO song_tag (song_id, tag_id)
SELECT s.id, t.id FROM song s JOIN tag t ON (s.external_source_id, t.type, t.name) IN (
    ('DP-0001','GENRE','Pop'),      ('DP-0001','MOOD','Energetic'), ('DP-0001','THEME','Celebration'), ('DP-0001','EVENT','New Year'),
    ('DP-0002','GENRE','EDM'),      ('DP-0002','MOOD','Energetic'), ('DP-0002','THEME','Party'),       ('DP-0002','EVENT','New Year'),
    ('DP-0003','GENRE','Piano'),    ('DP-0003','MOOD','Emotional'), ('DP-0003','THEME','Love'),        ('DP-0003','EVENT','Christmas'),
    ('DP-0004','GENRE','Pop'),      ('DP-0004','MOOD','Cheerful'),  ('DP-0004','THEME','Celebration'), ('DP-0004','EVENT','Christmas'),
    ('DP-0005','GENRE','EDM'),      ('DP-0005','MOOD','Energetic'), ('DP-0005','THEME','Party'),       ('DP-0005','EVENT','Summer'),
    ('DP-0006','GENRE','Pop'),      ('DP-0006','MOOD','Cheerful'),  ('DP-0006','THEME','Adventure'),   ('DP-0006','EVENT','Summer'),
    ('DP-0007','GENRE','Piano'),    ('DP-0007','MOOD','Relaxing'),  ('DP-0007','THEME','Background'),  ('DP-0007','EVENT','Christmas'),
    ('DP-0008','GENRE','Pop'),      ('DP-0008','MOOD','Cheerful'),  ('DP-0008','THEME','Party'),       ('DP-0008','EVENT','New Year'),
    ('DP-0009','GENRE','Lo-fi'),    ('DP-0009','MOOD','Relaxing'),  ('DP-0009','THEME','Background'),
    ('DP-0010','GENRE','Orchestral'),('DP-0010','MOOD','Dramatic'), ('DP-0010','THEME','Adventure'),
    ('DP-0011','GENRE','Acoustic'), ('DP-0011','MOOD','Cheerful'),  ('DP-0011','THEME','Celebration'), ('DP-0011','EVENT','Summer'),
    ('DP-0012','GENRE','Acoustic'), ('DP-0012','MOOD','Emotional'), ('DP-0012','THEME','Love'),
    ('DP-0013','GENRE','Pop'),      ('DP-0013','MOOD','Cheerful'),  ('DP-0013','THEME','Party'),       ('DP-0013','EVENT','Halloween'),
    ('DP-0014','GENRE','Lo-fi'),    ('DP-0014','MOOD','Dreamy'),    ('DP-0014','THEME','Background'),
    ('DP-0015','GENRE','EDM'),      ('DP-0015','MOOD','Energetic'), ('DP-0015','THEME','Party'),       ('DP-0015','EVENT','Holiday'),
    ('DP-0016','GENRE','Orchestral'),('DP-0016','MOOD','Emotional'),('DP-0016','THEME','Background'),  ('DP-0016','EVENT','Christmas'),
    ('DP-0017','GENRE','EDM'),      ('DP-0017','MOOD','Dreamy'),    ('DP-0017','THEME','Party'),       ('DP-0017','EVENT','Summer'),
    ('DP-0018','GENRE','Acoustic'), ('DP-0018','MOOD','Relaxing'),  ('DP-0018','THEME','Background'),
    ('DP-0019','GENRE','Orchestral'),('DP-0019','MOOD','Emotional'),('DP-0019','THEME','Celebration'), ('DP-0019','EVENT','Christmas'),
    ('DP-0020','GENRE','Orchestral'),('DP-0020','MOOD','Dramatic'), ('DP-0020','THEME','Celebration'), ('DP-0020','EVENT','New Year')
);
