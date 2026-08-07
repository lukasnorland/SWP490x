package com.funix.swp490x.mrs.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.AlreadyExistsException;
import software.amazon.awssdk.services.sesv2.model.CreateEmailIdentityRequest;
import software.amazon.awssdk.services.sesv2.model.CreateEmailIdentityResponse;
import software.amazon.awssdk.services.sesv2.model.DeleteEmailIdentityRequest;
import software.amazon.awssdk.services.sesv2.model.DeleteEmailIdentityResponse;
import software.amazon.awssdk.services.sesv2.model.GetEmailIdentityRequest;
import software.amazon.awssdk.services.sesv2.model.GetEmailIdentityResponse;
import software.amazon.awssdk.services.sesv2.model.VerificationStatus;

@ExtendWith(MockitoExtension.class)
class AwsSesIdentityServiceTest {

    @Mock
    private SesV2Client ses;

    private AwsSesIdentityService service;

    @BeforeEach
    void setUp() {
        service = new AwsSesIdentityService(ses);
    }

    @Test
    void aNewAddressStartsVerification() {
        given(ses.createEmailIdentity(any(CreateEmailIdentityRequest.class)))
                .willReturn(CreateEmailIdentityResponse.builder().build());

        assertThat(service.prepareRecipient("nina@example.com"))
                .isEqualTo(SesIdentityService.Outcome.VERIFICATION_SENT);
    }

    @Test
    void anAlreadyVerifiedAddressIsReportedWithoutRecreating() {
        given(ses.createEmailIdentity(any(CreateEmailIdentityRequest.class)))
                .willThrow(AlreadyExistsException.builder().message("exists").build());
        given(ses.getEmailIdentity(any(GetEmailIdentityRequest.class)))
                .willReturn(GetEmailIdentityResponse.builder()
                        .verifiedForSendingStatus(true)
                        .verificationStatus(VerificationStatus.SUCCESS)
                        .build());

        assertThat(service.prepareRecipient("nina@example.com"))
                .isEqualTo(SesIdentityService.Outcome.ALREADY_VERIFIED);

        then(ses).should(never()).deleteEmailIdentity(any(DeleteEmailIdentityRequest.class));
    }

    @Test
    void aPendingAddressIsDeletedAndRecreatedSoAwsResendsTheLink() {
        given(ses.createEmailIdentity(any(CreateEmailIdentityRequest.class)))
                .willThrow(AlreadyExistsException.builder().message("exists").build())
                .willReturn(CreateEmailIdentityResponse.builder().build());
        given(ses.getEmailIdentity(any(GetEmailIdentityRequest.class)))
                .willReturn(GetEmailIdentityResponse.builder()
                        .verifiedForSendingStatus(false)
                        .verificationStatus(VerificationStatus.PENDING)
                        .build());
        given(ses.deleteEmailIdentity(any(DeleteEmailIdentityRequest.class)))
                .willReturn(DeleteEmailIdentityResponse.builder().build());

        assertThat(service.prepareRecipient("nina@example.com"))
                .isEqualTo(SesIdentityService.Outcome.VERIFICATION_SENT);

        then(ses).should().deleteEmailIdentity(any(DeleteEmailIdentityRequest.class));
    }
}
