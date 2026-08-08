package com.funix.swp490x.mrs.security;

import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated principal. Carries the display name, role and forced-password
 * flag that the shell (spec 4.0) and the login flow need.
 */
public class MrsUserDetails implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private final String displayName;
    private final String passwordHash;
    private final Role role;
    private final boolean active;
    private final boolean mustChangePassword;
    private final boolean accountNonLocked;

    public MrsUserDetails(User user, boolean accountNonLocked) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.displayName = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.active = user.getStatus() == UserStatus.ACTIVE;
        this.mustChangePassword = user.isMustChangePassword();
        this.accountNonLocked = accountNonLocked;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.getAuthority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** The login form authenticates on email, so that is the username. */
    @Override
    public String getUsername() {
        return email;
    }

    /** Deactivated accounts cannot authenticate (FT-01 AC-03). */
    @Override
    public boolean isEnabled() {
        return active;
    }

    /**
     * False while the account is inside a lockout window, so even correct
     * credentials are rejected for the remainder of it (FT-01 NAC-01).
     */
    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isCustomer() {
        return role == Role.CUSTOMER;
    }

    /** ADMIN and Content Designer both reach the curation screens (spec 2.1). */
    public boolean isCurator() {
        return role == Role.ADMIN || role == Role.CONTENT_DESIGNER;
    }

    /**
     * SessionRegistry looks principals up by equality. Email is the login name,
     * so two MrsUserDetails for the same address must match across requests.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MrsUserDetails that)) {
            return false;
        }
        return email.equalsIgnoreCase(that.email);
    }

    @Override
    public int hashCode() {
        return email.toLowerCase().hashCode();
    }
}
