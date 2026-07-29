package com.funix.swp490x.mrs.web;

/**
 * URL of every page in the site map (spec 2.2), keyed by its Page ID so screen
 * specs and routes stay traceable to each other.
 */
public final class Routes {

    /** P-00 Login. */
    public static final String LOGIN = "/login";
    /** P-01 Password Reset — step 1, request a link. */
    public static final String PASSWORD_RESET = "/password-reset";
    /** P-01 Password Reset — step 2, set a new password from an emailed link. */
    public static final String PASSWORD_RESET_SET = "/password-reset/set";
    /** FT-09 forced change on first login. */
    public static final String PASSWORD_CHANGE = "/account/password";

    /** P-02 Search & Recommendation. */
    public static final String SEARCH = "/search";
    /** P-03a My Playlists. */
    public static final String PLAYLISTS = "/playlists";
    /** P-04a Shared Workspace. */
    public static final String WORKSPACE = "/workspace";
    /** P-05 My Profile & Playlist History. */
    public static final String PROFILE = "/profile";

    /** P-06a Admin — User Management. */
    public static final String ADMIN_USERS = "/admin/users";
    /** P-06b Admin — Song Catalog & Metadata. */
    public static final String ADMIN_CATALOG = "/admin/catalog";
    /** P-06c Admin — Catalog Import. */
    public static final String ADMIN_IMPORT = "/admin/import";
    /** P-06d Admin — System Settings. */
    public static final String ADMIN_SETTINGS = "/admin/settings";
    /** P-06e Admin — Audit & Recommendation Log. */
    public static final String ADMIN_LOGS = "/admin/logs";

    /**
     * Roots serving static assets rather than pages.
     *
     * <p>Held in one place because every request-scoped rule has to agree about
     * them: they are permitted anonymously, they are not held back by the forced
     * password change, and they do not need a CSRF token primed. Listing them
     * separately let {@code /vendor/**} reach the security config but not the
     * interceptor, which redirected Bootstrap's stylesheet to the change-password
     * page and left that screen unstyled.
     */
    public static final String[] STATIC_ASSETS = {
        "/css/**", "/js/**", "/vendor/**", "/fonts/**", "/images/**", "/favicon.ico"
    };

    private Routes() {
    }
}
