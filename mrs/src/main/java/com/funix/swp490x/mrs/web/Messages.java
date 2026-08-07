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

    private Messages() {
    }
}
