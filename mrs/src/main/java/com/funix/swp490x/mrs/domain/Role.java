package com.funix.swp490x.mrs.domain;

/**
 * The three system roles (BR-01: exactly one role per account).
 *
 * <p>Each role also carries the landing page it lands on after login and the
 * label shown in the top-bar role badge, both defined by Screen Design Spec
 * section 2.1.
 */
public enum Role {

    ADMIN("ADMIN", "/admin/users"),
    CONTENT_DESIGNER("Content Designer", "/search"),
    CUSTOMER("Customer", "/workspace");

    private final String displayName;
    private final String landingPath;

    Role(String displayName, String landingPath) {
        this.displayName = displayName;
        this.landingPath = landingPath;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLandingPath() {
        return landingPath;
    }

    /** Spring Security authority name, e.g. {@code ROLE_CONTENT_DESIGNER}. */
    public String getAuthority() {
        return "ROLE_" + name();
    }
}
