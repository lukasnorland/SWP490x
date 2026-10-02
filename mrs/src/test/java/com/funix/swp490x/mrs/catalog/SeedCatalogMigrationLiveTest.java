package com.funix.swp490x.mrs.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Runs real Flyway migrations in isolated databases, never the configured application database. */
@EnabledIfEnvironmentVariable(named = "MRS_LIVE_DB", matches = "1")
class SeedCatalogMigrationLiveTest {

    @Test
    void freshDatabaseGetsTheCompleteSearchableSnapshot() throws Exception {
        try (TestDatabase db = new TestDatabase()) {
            assertThat(db.flyway(null).migrate().migrationsExecuted).isEqualTo(3);
            assertThat(db.count("SELECT COUNT(*) FROM song")).isEqualTo(5741);
            assertThat(db.count("SELECT COUNT(*) FROM tag")).isEqualTo(4899);
            assertThat(db.count("SELECT COUNT(*) FROM song_tag")).isEqualTo(64168);
            assertThat(db.count("SELECT COUNT(*) FROM song WHERE audio_url IS NOT NULL"))
                    .isEqualTo(5741);
            assertThat(db.count("SELECT COUNT(*) FROM song s WHERE NOT EXISTS "
                    + "(SELECT 1 FROM song_tag st WHERE st.song_id = s.id)")).isZero();
            assertThat(db.count("SELECT COUNT(*) FROM song WHERE source_provider = 'NCS'"))
                    .isEqualTo(1648);
            assertThat(db.count("SELECT COUNT(*) FROM tag WHERE name = '(G)I-DLE (여자아이들)'"))
                    .isEqualTo(1);
            assertThat(db.count("SELECT COUNT(*) FROM users")).isEqualTo(1);
            assertThat(db.count("SELECT COUNT(*) FROM playlist")).isZero();
            assertThat(db.flyway(null).migrate().migrationsExecuted).isZero();
            assertThat(db.count("SELECT COUNT(*) FROM song")).isEqualTo(5741);

            // Match a manually populated application database baselined at V2.
            db.execute("DROP TABLE flyway_schema_history");
            assertThat(db.flyway(null).migrate().migrationsExecuted).isEqualTo(1);
            assertThat(db.count("SELECT COUNT(*) FROM song")).isEqualTo(5741);
            assertThat(db.count("SELECT COUNT(*) FROM tag")).isEqualTo(4899);
            assertThat(db.count("SELECT COUNT(*) FROM song_tag")).isEqualTo(64168);
        }
    }

    @Test
    void existingIdsEditsTagsAndPlaylistsSurviveSeeding() throws Exception {
        try (TestDatabase db = new TestDatabase()) {
            db.flyway("2").migrate();
            // Both song and tag IDs deliberately collide with IDs in the snapshot.
            db.execute("INSERT INTO song (id, title, source_provider, external_source_id) "
                    + "VALUES (1, 'Local track', 'OneOff', 'local-track')");
            db.execute("INSERT INTO song (id, title, artist, source_provider, external_source_id, "
                    + "source_etag, version) VALUES (7000, 'Curator title', 'Curator artist', "
                    + "'EpidemicSound', '003c5571-5014-387b-978c-2836125178a4', 'edited', 7)");
            db.execute("INSERT INTO tag (id, type, name) VALUES (1215, 'MOOD', 'Local mood')");
            db.execute("INSERT INTO tag (id, type, name) VALUES (9000, 'ARTIST', 'Sugar Blizz')");
            db.execute("INSERT INTO song_tag (song_id, tag_id) VALUES (7000, 1215), (1, 1215)");
            db.execute("INSERT INTO playlist (name, owner_id, created_by, last_modified_by) "
                    + "SELECT 'Existing playlist', id, id, id FROM users LIMIT 1");
            db.execute("INSERT INTO playlist_song (playlist_id, song_id, position) "
                    + "SELECT id, 7000, 1 FROM playlist");

            assertThat(db.flyway(null).migrate().migrationsExecuted).isEqualTo(1);
            assertThat(db.count("SELECT COUNT(*) FROM song")).isEqualTo(5742);
            assertThat(db.count("SELECT COUNT(*) FROM song WHERE id = 7000 "
                    + "AND title = 'Curator title' AND artist = 'Curator artist' "
                    + "AND source_etag = 'edited' AND version = 7")).isEqualTo(1);
            assertThat(db.count("SELECT COUNT(*) FROM song_tag WHERE song_id = 7000"))
                    .isEqualTo(1);
            assertThat(db.count("SELECT COUNT(*) FROM song_tag WHERE song_id = 7000 "
                    + "AND tag_id = 1215")).isEqualTo(1);
            assertThat(db.count("SELECT COUNT(*) FROM playlist_song WHERE song_id = 7000 "
                    + "AND position = 1")).isEqualTo(1);
            assertThat(db.count("SELECT COUNT(*) FROM tag WHERE type = 'ARTIST' "
                    + "AND name = 'Sugar Blizz'")).isEqualTo(1);
            assertThat(db.count("SELECT COUNT(*) FROM song_tag WHERE tag_id = 9000"))
                    .isPositive();
            assertThat(db.count("SELECT COUNT(*) FROM song_tag st JOIN song s ON s.id = st.song_id "
                    + "WHERE st.tag_id = 1215 AND s.id NOT IN (1, 7000)")).isZero();
            assertThat(db.count("SELECT COUNT(*) FROM song WHERE id = 1 AND title = 'Local track'"))
                    .isEqualTo(1);
        }
    }

    private static final class TestDatabase implements AutoCloseable {
        private final String name = "mrs_seed_test_" + UUID.randomUUID().toString().replace("-", "");
        private final String username = System.getenv("DB_USERNAME");
        private final String password = System.getenv("DB_PASSWORD");
        private final String url;
        private final Connection admin;
        private final Connection connection;

        TestDatabase() throws SQLException {
            String configured = System.getenv("DB_URL");
            if (configured == null || username == null || password == null) {
                throw new IllegalArgumentException("Set DB_URL, DB_USERNAME and DB_PASSWORD");
            }
            int databaseStart = configured.indexOf('/', "jdbc:mysql://".length());
            if (!configured.startsWith("jdbc:mysql://") || databaseStart < 0) {
                throw new IllegalArgumentException("DB_URL must be a jdbc:mysql://host/database URL");
            }
            int queryStart = configured.indexOf('?', databaseStart);
            String options = queryStart < 0 ? "" : configured.substring(queryStart);
            String server = configured.substring(0, databaseStart + 1);
            url = server + name + options;
            admin = DriverManager.getConnection(server + options, username, password);
            try (var statement = admin.createStatement()) {
                statement.execute("CREATE DATABASE `" + name
                        + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            }
            connection = DriverManager.getConnection(url, username, password);
        }

        Flyway flyway(String target) {
            var configuration = Flyway.configure().dataSource(url, username, password)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true).baselineVersion("2");
            if (target != null) {
                configuration.target(target);
            }
            return configuration.load();
        }

        void execute(String sql) throws SQLException {
            try (var statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }

        long count(String sql) throws SQLException {
            try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
                result.next();
                return result.getLong(1);
            }
        }

        @Override
        public void close() throws SQLException {
            try {
                connection.close();
                try (var statement = admin.createStatement()) {
                    statement.execute("DROP DATABASE `" + name + "`");
                }
            } finally {
                admin.close();
            }
        }
    }
}
