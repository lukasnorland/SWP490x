package com.funix.swp490x.mrs.web;

/**
 * User-facing strings the SRS fixes by code (5.2 Application Messages List).
 *
 * <p>Held here so a screen and the document cannot drift apart silently. Only
 * the codes the built screens use are listed; the rest arrive with their
 * screens.
 */
public final class Messages {

    /** MSG_012. */
    public static final String DUPLICATE_EMAIL =
            "This email is already registered to another account.";

    /** MSG_021. */
    public static final String USER_CREATED = "User created successfully. The login email and "
            + "initial password have been sent to their registered address.";

    /** MSG_022. */
    public static final String USER_CREATED_EMAIL_FAILED = "The account was created but the "
            + "notification email could not be sent. You can resend it from the User Account List.";

    /**
     * The catalogue has no code for a malformed address. It is worth its own
     * message rather than folding into MSG_012, which would claim the address
     * belongs to someone.
     */
    public static final String INVALID_EMAIL =
            "Enter a valid email address — the initial password is sent to it.";

    /**
     * The catalogue has no code for a resend either, so these two say what
     * actually happened: a resend cannot repeat the original password (see
     * {@code UserAccountService.reissueInitialPassword}).
     */
    public static final String CREDENTIALS_RESENT =
            "A new initial password has been sent to the user's registered address.";

    public static final String CREDENTIALS_RESEND_FAILED = "A new initial password was set but "
            + "the notification email could not be sent. The previous password no longer works, "
            + "so please try resending.";

    /** Soft-delete outcome (spec 4.9). Catalogue has no dedicated code yet. */
    public static final String USER_DEACTIVATED =
            "Account deactivated. Any open session will end on the next request.";

    public static final String USER_DEACTIVATED_EMAIL_FAILED = "Account deactivated, but the "
            + "notification email could not be sent. The user has not been told.";

    public static final String USER_REACTIVATED = "Account reactivated. The user can sign in again.";

    public static final String USER_ROLE_CHANGED = "Role updated. The user must sign in again "
            + "before the new permissions apply.";

    public static final String USER_ROLE_CHANGED_EMAIL_FAILED = "Role updated, but the "
            + "notification email could not be sent. The user has not been told.";

    /** Appended to the role-change or deactivation flash when owned playlists were reassigned. */
    public static String playlistsTransferred(int count) {
        return count + (count == 1 ? " playlist" : " playlists")
                + " the user owned were reassigned.";
    }

    public static final String SELF_MODIFICATION_FORBIDDEN =
            "You cannot deactivate, change the role of, or resend credentials for your own account.";

    /**
     * Outcomes of the P-06a "Verify for SES" control. Not in the SRS catalogue —
     * they exist because the sandbox forces recipient verification before BR-15
     * can deliver, and the CLI is awkward for a live demo.
     */
    public static final String SES_VERIFICATION_SENT = "Amazon SES has emailed a verification "
            + "link to that address. Ask the owner to confirm it within 24 hours, then create "
            + "the account.";

    public static final String SES_ALREADY_VERIFIED =
            "That address is already verified with Amazon SES — you can create the account now.";

    public static final String SES_RECIPIENT_NOT_VERIFIED =
            "Verify that address with SES before creating the account. Click Verify for SES, "
                    + "wait for the owner to confirm the AWS link, then click Verify for SES again.";

    public static final String SES_VERIFICATION_FAILED =
            "Could not start SES verification for that address. Run "
                    + "'aws login --profile mrs-admin', confirm that profile still works, "
                    + "then try again.";

    /** Landing-page self-registration request outcome messages. */
    public static final String REGISTER_REQUEST_SENT = "Your registration request has been sent. "
            + "An ADMIN will review it and contact you.";

    public static final String REGISTER_REQUEST_INVALID_EMAIL =
            "Enter a valid email address so ADMIN can contact you.";

    public static final String REGISTER_REQUEST_EMAIL_FAILED =
            "Your request could not be sent right now. Please try again.";

    /**
     * Outcomes of the P-06b catalog import (UC-28). The catalogue has no codes
     * for the S3 path, which reports counts rather than a fixed sentence, so
     * these cover only the cases with nothing to count.
     */
    public static final String IMPORT_NO_CHANGE =
            "Nothing to import — every staged song is already in the catalog.";

    public static final String IMPORT_STARTED =
            "Import started. Keep this page open to watch progress; the summary appears when it finishes.";

    public static final String IMPORT_ALREADY_RUNNING =
            "An import is already running. Wait for it to finish, then check the summary.";

    public static final String IMPORT_FAILED =
            "The import could not be completed. Check that the staged catalog is reachable, "
                    + "then try again — anything already applied is kept.";

    public static final String UPLOAD_SYNC_SKIPPED =
            "The files were staged, but an import is already running. Press Sync Catalog when it finishes.";

    public static final String MEDIA_UPLOAD_EMPTY =
            "Drop one or more audio files, fill in each song, then upload.";

    public static final String MEDIA_UPLOAD_ALL_REJECTED =
            "None of the songs could be staged. Fix the issues listed below and try again.";

    public static final String MEDIA_UPLOAD_TOO_LARGE =
            "A file is larger than 100 MB (audio) or 5 MB (cover), or the batch is over 1.1 GB. "
                    + "Use a smaller file and try again.";

    /**
     * Outcomes of P-06b song edit and delete (UC-29). Songs have no clone
     * option on a stale save (BR-06, DC-02) — refresh only.
     */
    public static final String SONG_SAVED = "Song updated.";

    public static final String SONG_UNKNOWN_CLASSIFICATION =
            "Pick a listed MusicBrainz genre or mood. Unknown names cannot be saved as Genre or Mood.";

    public static final String SONG_SAVE_FAILED =
            "The staged catalog object could not be updated. The song was not changed — try again.";

    public static final String SONG_DELETED = "Song removed from the catalog.";

    public static final String SONG_STALE =
            "This song changed while you were editing. Refresh the page and try again.";

    public static final String SONG_NOT_FOUND = "That song is no longer in the catalog.";

    public static final String SONG_DELETE_FAILED =
            "The song could not be removed from staging. It is still in the catalog — try again.";

    /**
     * Outcomes of P-03a / P-03b (FT-06 – FT-08). The catalogue has no codes for
     * the playlist screens yet, so these say what happened in the same voice.
     */
    public static final String PLAYLIST_CREATED = "Playlist created.";

    public static final String PLAYLIST_CREATED_WITH_SONG =
            "Playlist created and the song added to it.";

    public static final String PLAYLIST_CREATED_FROM_RESULTS =
            "Playlist created with every song from the search results.";

    public static final String SEARCH_NO_RESULTS_TO_ADD =
            "The search has no results to put in a playlist — refine it first.";

    public static final String PLAYLIST_RENAMED = "Playlist renamed.";

    public static final String PLAYLIST_DUPLICATED =
            "Playlist duplicated. This copy is a Draft you can edit.";

    public static final String PLAYLIST_NAME_REQUIRED = "Give the playlist a name.";

    public static final String PLAYLIST_NAME_TAKEN =
            "A playlist with that name already exists. Choose another.";

    public static final String PLAYLIST_NOT_FOUND =
            "That playlist no longer exists, or it is not shared with you.";

    public static final String SONG_ADDED_TO_PLAYLIST = "Song added to the playlist.";

    public static final String SONG_ALREADY_IN_PLAYLIST =
            "That song is already in the playlist — nothing was changed.";

    public static final String SONG_REMOVED_FROM_PLAYLIST = "Song removed from the playlist.";

    public static final String PLAYLIST_LOCKED =
            "Published playlists are locked — unpublish it first, then edit.";

    public static final String PLAYLIST_PUBLISH_EMPTY =
            "Add at least one song before publishing.";

    public static final String PLAYLIST_PUBLISHED =
            "Playlist published. It is now read-only in the Shared Workspace.";

    public static final String PLAYLIST_UNPUBLISHED =
            "Playlist returned to Draft. You can edit it again.";

    public static final String PLAYLIST_DELETED = "Playlist deleted.";

    public static final String PLAYLIST_DELETE_PUBLISHED =
            "A published playlist cannot be deleted. Unpublish it first.";

    public static final String PLAYLIST_DELETE_NOT_OWNER =
            "Only the owner can delete this playlist.";

    public static final String COLLABORATOR_ADDED =
            "That Content Designer can now edit this playlist.";

    public static final String COLLABORATOR_ALREADY =
            "That Content Designer is already a collaborator.";

    public static final String COLLABORATOR_REMOVED = "Collaborator removed.";

    public static final String COLLABORATOR_INVALID =
            "Share edit rights with an active Content Designer who is not already on this playlist.";

    public static final String COLLABORATOR_MANAGE_OWNER_ONLY =
            "Only the owner can share this playlist.";

    public static final String SUCCESSOR_REQUIRED =
            "Choose who should own each playlist before continuing.";

    public static final String PLAYLIST_PICK_ONE =
            "Choose a playlist, or create a new one.";

    /** P-02 FT-04: contextual query length. */
    public static final String SEARCH_QUERY_LENGTH =
            "Describe the playlist in 10–200 characters.";

    public static final String SONGS_ADDED_TO_PLAYLIST = "Songs added to the playlist.";

    private Messages() {
    }
}
