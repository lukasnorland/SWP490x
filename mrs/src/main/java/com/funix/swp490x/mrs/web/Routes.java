package com.funix.swp490x.mrs.web;

/**
 * URL of every page in the site map (spec 2.2), keyed by its Page ID so screen
 * specs and routes stay traceable to each other.
 */
public final class Routes {

    /** P-00 Login. */
    public static final String LOGIN = "/login";
    /** Public registration intent request from the login landing page. */
    public static final String REGISTER_REQUEST = "/register-request";
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
    /** P-06a — resend the credentials message for an account (UC-07 E3). */
    public static final String ADMIN_USER_RESEND = "/admin/users/{id}/resend-credentials";
    /** P-06a — soft-delete an account (spec 4.9 / FT-01 AC-03). */
    public static final String ADMIN_USER_DEACTIVATE = "/admin/users/{id}/deactivate";
    /** P-06a — restore a deactivated account. */
    public static final String ADMIN_USER_REACTIVATE = "/admin/users/{id}/reactivate";
    /** P-06a — change role (UC-06); ADMIN is not assignable. */
    public static final String ADMIN_USER_ROLE = "/admin/users/{id}/role";
    /**
     * P-06a — declare the typed address with Amazon SES so the sandbox will
     * accept mail to it (CreateEmailIdentity). Separate from account creation.
     */
    public static final String ADMIN_USER_PREPARE_RECIPIENT = "/admin/users/prepare-recipient";
    /** P-06b Admin — Song Catalog & Metadata. */
    public static final String ADMIN_CATALOG = "/admin/catalog";
    /** P-06c Admin — Catalog Import. */
    public static final String ADMIN_IMPORT = "/admin/import";
    /** P-06c — run the import now, reading the staged JSON from S3 (UC-28). */
    public static final String ADMIN_IMPORT_RUN = "/admin/import/run";
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
