package com.funix.swp490x.mrs.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.AlreadyExistsException;
import software.amazon.awssdk.services.sesv2.model.CreateEmailIdentityRequest;
import software.amazon.awssdk.services.sesv2.model.DeleteEmailIdentityRequest;
import software.amazon.awssdk.services.sesv2.model.GetEmailIdentityRequest;
import software.amazon.awssdk.services.sesv2.model.GetEmailIdentityResponse;
import software.amazon.awssdk.services.sesv2.model.NotFoundException;
import software.amazon.awssdk.services.sesv2.model.VerificationStatus;

/**
 * SES v2 API implementation of {@link SesIdentityService}.
 *
 * <p>An address that already exists but is still pending confirmation is
 * deleted and recreated so AWS emails a fresh link — CreateEmailIdentity alone
 * does not resend, and the previous link expires after 24 hours.
 */
public class AwsSesIdentityService implements SesIdentityService {

    private static final Logger log = LoggerFactory.getLogger(AwsSesIdentityService.class);

    private final SesV2Client ses;

    public AwsSesIdentityService(SesV2Client ses) {
        this.ses = ses;
    }

    @Override
    public Outcome prepareRecipient(String email) {
        String address = email.trim();
        try {
            try {
                return create(address);
            } catch (AlreadyExistsException existing) {
                return handleExisting(address);
            }
        } catch (RuntimeException e) {
            // Includes SdkClientException and IllegalStateException from a
            // failed ProcessCredentialsProvider refresh (e.g. aws login expired
            // or the CLI missing from PATH).
            throw new SesIdentityException("SES rejected the identity request for " + address, e);
        }
    }

    @Override
    public boolean isVerified(String email) {
        String address = email.trim();
        try {
            GetEmailIdentityResponse identity = ses.getEmailIdentity(GetEmailIdentityRequest.builder()
                    .emailIdentity(address)
                    .build());
            return isVerified(identity);
        } catch (NotFoundException missing) {
            return false;
        } catch (RuntimeException e) {
            throw new SesIdentityException("SES could not read the identity for " + address, e);
        }
    }

    private Outcome create(String address) {
        ses.createEmailIdentity(CreateEmailIdentityRequest.builder()
                .emailIdentity(address)
                .build());
        log.info("Started SES verification for {}", address);
        return Outcome.VERIFICATION_SENT;
    }

    private Outcome handleExisting(String address) {
        GetEmailIdentityResponse identity;
        try {
            identity = ses.getEmailIdentity(GetEmailIdentityRequest.builder()
                    .emailIdentity(address)
                    .build());
        } catch (NotFoundException missing) {
            // Race: listed as existing then gone. Recreate.
            return create(address);
        }

        if (isVerified(identity)) {
            return Outcome.ALREADY_VERIFIED;
        }

        ses.deleteEmailIdentity(DeleteEmailIdentityRequest.builder()
                .emailIdentity(address)
                .build());
        return create(address);
    }

    private static boolean isVerified(GetEmailIdentityResponse identity) {
        if (Boolean.TRUE.equals(identity.verifiedForSendingStatus())) {
            return true;
        }
        return identity.verificationStatus() == VerificationStatus.SUCCESS;
    }
}
