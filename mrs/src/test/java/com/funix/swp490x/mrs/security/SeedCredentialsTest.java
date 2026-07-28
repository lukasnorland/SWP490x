package com.funix.swp490x.mrs.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Guards the dev-only seed credential in migration V2 against the password the
 * README documents. A mismatch here means nobody can sign in to a freshly
 * migrated database.
 */
class SeedCredentialsTest {

    /** The hash all three demo accounts share in V2__seed_sample_data.sql. */
    private static final String SEEDED_HASH =
            "$2a$10$S6Ja2VOI/uaySJ3zx834I.H4/f7hK/KGUZu/FzRatTKxAki9FLhQO";

    private static final String DOCUMENTED_PASSWORD = "Admin@2026";

    @Test
    void seededHashMatchesTheDocumentedDemoPassword() {
        assertThat(new BCryptPasswordEncoder().matches(DOCUMENTED_PASSWORD, SEEDED_HASH)).isTrue();
    }
}
