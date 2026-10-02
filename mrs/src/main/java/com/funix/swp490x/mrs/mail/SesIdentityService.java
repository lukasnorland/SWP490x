package com.funix.swp490x.mrs.mail;

/** Prepares recipient verification for the SES sandbox through the API, separately from account creation. */
public interface SesIdentityService {

    /**
     * Starts or reports verification for {@code email}.
     *
     * @throws SesIdentityException when SES cannot be reached or rejects the call
     */
    Outcome prepareRecipient(String email);

    /**
     * Whether SES already accepts {@code email} as a sandbox recipient.
     *
     * @return {@code false} when the identity is missing or still pending
     * @throws SesIdentityException when SES cannot be reached or rejects the call
     */
    boolean isVerified(String email);

    /** What happened for the address ADMIN just typed. */
    enum Outcome {
        /** A fresh verification link was emailed by AWS. */
        VERIFICATION_SENT,
        /** The address is already usable as a sandbox recipient. */
        ALREADY_VERIFIED
    }
}
