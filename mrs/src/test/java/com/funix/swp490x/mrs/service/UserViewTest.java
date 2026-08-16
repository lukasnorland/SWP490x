package com.funix.swp490x.mrs.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UserViewTest {

    @Test
    void of_shouldCopyPublicFieldsAndOmitThePasswordHash() {
        User user = new User();
        user.setId(7L);
        user.setUsername("Nina Designer");
        user.setEmail("nina@mrs.local");
        user.setPasswordHash("{bcrypt}secret");
        user.setRole(Role.CONTENT_DESIGNER);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(true);
        user.setCreatedAt(LocalDateTime.of(2026, 8, 16, 10, 0));

        UserView view = UserView.of(user);

        assertThat(view.id()).isEqualTo(7L);
        assertThat(view.username()).isEqualTo("Nina Designer");
        assertThat(view.email()).isEqualTo("nina@mrs.local");
        assertThat(view.role()).isEqualTo(Role.CONTENT_DESIGNER);
        assertThat(view.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(view.mustChangePassword()).isTrue();
        assertThat(view.createdAt()).isEqualTo(LocalDateTime.of(2026, 8, 16, 10, 0));
        assertThat(view.toString()).doesNotContain("secret").doesNotContain("{bcrypt}");
    }
}
