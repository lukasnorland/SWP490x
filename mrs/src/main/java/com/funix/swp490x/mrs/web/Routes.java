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
    /** FT-04 — interpret a contextual query once, then redirect to GET filters. */
    public static final String SEARCH_INTERPRET = "/search/interpret";
    /** Preview-bar queue for the current Search filters, in relevance order. */
    public static final String SEARCH_PLAY_QUEUE = "/search/play-queue";
    /** FT-06 — new Draft holding every song the current Search filters match. */
    public static final String SEARCH_CREATE_PLAYLIST = "/search/create-playlist";
    /** Authenticated song browse (listen / preview); reuses the catalog list. */
    public static final String SONGS = "/songs";
    /** Preview-bar queue: every playable row matching the current Songs filters. */
    public static final String SONGS_PLAY_QUEUE = "/songs/play-queue";
    /** P-03a My Playlists. */
    public static final String PLAYLISTS = "/playlists";
    /** P-03b Playlist Detail / Editor. */
    public static final String PLAYLIST = "/playlists/{id}";
    /** FT-06 — add a song from the Songs table or P-02 (UC-14). */
    public static final String PLAYLIST_SONGS = "/playlists/{id}/songs";
    /** FT-06 — drop a song and renumber the rest 1..N (AC-03). */
    public static final String PLAYLIST_SONG_REMOVE = "/playlists/{id}/songs/{songId}/remove";
    /** FT-06 — swap a song with its neighbour (AC-03). */
    public static final String PLAYLIST_SONG_MOVE = "/playlists/{id}/songs/{songId}/move";
    /** FT-06 — rename a Draft playlist (DC-08). */
    public static final String PLAYLIST_RENAME = "/playlists/{id}/rename";
    /** Duplicate into a new Draft owned by the caller. */
    public static final String PLAYLIST_DUPLICATE = "/playlists/{id}/duplicate";
    /** BR-03 — grant a Content Designer edit rights. */
    public static final String PLAYLIST_COLLABORATORS = "/playlists/{id}/collaborators";
    /** BR-03 — drop a collaborator grant. */
    public static final String PLAYLIST_COLLABORATOR_REMOVE =
            "/playlists/{id}/collaborators/{userId}/remove";
    /** FT-06 — delete a Draft playlist (DC-05). */
    public static final String PLAYLIST_DELETE = "/playlists/{id}/delete";
    /** FT-07 — publish to the Shared Workspace; needs at least one song (BR-05). */
    public static final String PLAYLIST_PUBLISH = "/playlists/{id}/publish";
    /** FT-07 — return a Published playlist to Draft so it can be edited (DC-08). */
    public static final String PLAYLIST_UNPUBLISH = "/playlists/{id}/unpublish";
    /** FT-08 — CSV of the playlist contents in playing order. */
    public static final String PLAYLIST_EXPORT = "/playlists/{id}/export.csv";
    /** P-04a Shared Workspace. */
    public static final String WORKSPACE = "/workspace";
    /** P-04b Published Playlist View. */
    public static final String WORKSPACE_PLAYLIST = "/workspace/{id}";
    /** P-05 My Profile & Playlist History. */
    public static final String PROFILE = "/profile";
    /** P-05 / UC-05 — save a new display name without re-login. */
    public static final String PROFILE_NAME = "/profile/name";
    /** P-05 / UC-05 — change password from the profile (BR-12). */
    public static final String PROFILE_PASSWORD = "/profile/password";

    /** P-06a Admin — User Management. */
    public static final String ADMIN_USERS = "/admin/users";
    /** P-06a — resend the credentials message for an account (UC-07 E3). */
    public static final String ADMIN_USER_RESEND = "/admin/users/{id}/resend-credentials";
    /** P-06a — soft-delete an account (spec 4.9 / FT-01 AC-03). */
    public static final String ADMIN_USER_DEACTIVATE = "/admin/users/{id}/deactivate";
    /** P-06a — restore a deactivated account. */
    public static final String ADMIN_USER_REACTIVATE = "/admin/users/{id}/reactivate";
    /** P-06a — pick a successor for each playlist before deactivate or demote. */
    public static final String ADMIN_USER_REASSIGN = "/admin/users/{id}/reassign";
    /** P-06a — change role (UC-06); ADMIN is not assignable. */
    public static final String ADMIN_USER_ROLE = "/admin/users/{id}/role";
    /**
     * P-06a — declare the typed address with Amazon SES so the sandbox will
     * accept mail to it (CreateEmailIdentity). Separate from account creation.
     */
    public static final String ADMIN_USER_PREPARE_RECIPIENT = "/admin/users/prepare-recipient";
    /** P-06b Admin — Song Catalog & Metadata. */
    /** P-06f — every playlist in the system, read-only, for ADMIN oversight. */
    public static final String ADMIN_PLAYLISTS = "/admin/playlists";
    public static final String ADMIN_PLAYLIST = "/admin/playlists/{id}";

    public static final String ADMIN_CATALOG = "/admin/catalog";
    /** P-06b — save an edited song (UC-29). */
    public static final String ADMIN_CATALOG_SONG = "/admin/catalog/{id}";
    /** P-06b — delete a song (UC-29). */
    public static final String ADMIN_CATALOG_SONG_DELETE = "/admin/catalog/{id}/delete";
    /** P-06b — upload audio, cover art and metadata; the server writes the JSON. */
    public static final String ADMIN_CATALOG_SONGS = "/admin/catalog/songs";
    /** P-06b — sync the staged song-data JSON into MySQL now (UC-28). */
    public static final String ADMIN_CATALOG_SYNC = "/admin/catalog/sync";
    /** P-06b — live progress of the sync currently running (or last finished). */
    public static final String ADMIN_CATALOG_SYNC_STATUS = "/admin/catalog/sync/status";
    /** P-06b — typeahead for genre / mood / tag fields. */
    public static final String ADMIN_CATALOG_TAG_SUGGEST = "/admin/catalog/tags/suggest";
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
