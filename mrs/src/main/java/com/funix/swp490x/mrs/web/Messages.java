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

    public static final String USER_REACTIVATED = "Account reactivated. The user can sign in again.";

    public static final String USER_ROLE_CHANGED = "Role updated. The user must sign in again "
            + "before the new permissions apply.";

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
     * Outcomes of the P-06c catalog import (UC-28). The catalogue has no codes
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
            "The files were staged, but an import is already running. Press Run import when it finishes.";

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

    public static final String SONG_SAVE_FAILED =
            "The staged catalog object could not be updated. The song was not changed — try again.";

    public static final String SONG_DELETED = "Song removed from the catalog.";

    public static final String SONG_STALE =
            "This song changed while you were editing. Refresh the page and try again.";

    public static final String SONG_NOT_FOUND = "That song is no longer in the catalog.";

    public static final String SONG_DELETE_FAILED =
            "The song could not be removed from staging. It is still in the catalog — try again.";

    private Messages() {
    }
}
