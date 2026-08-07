package com.funix.swp490x.mrs.mail;

/**
 * Declares a mailbox with Amazon SES so the sandbox will accept mail to it.
 *
 * <p>SMTP credentials cannot do this — only the SES API can — which is why the
 * verify button on P-06a is a separate path from the credentials message of
 * BR-15. Production access removes the need for recipient verification
 * entirely; until then every real inbox has to pass through here (or the
 * equivalent CLI) before {@code Create account}.
 */
public interface SesIdentityService {

    /**
     * Starts or reports verification for {@code email}.
     *
     * @throws SesIdentityException when SES cannot be reached or rejects the call
     */
    Outcome prepareRecipient(String email);

    /** What happened for the address ADMIN just typed. */
    enum Outcome {
        /** A fresh verification link was emailed by AWS. */
        VERIFICATION_SENT,
        /** The address is already usable as a sandbox recipient. */
        ALREADY_VERIFIED
    }
}
