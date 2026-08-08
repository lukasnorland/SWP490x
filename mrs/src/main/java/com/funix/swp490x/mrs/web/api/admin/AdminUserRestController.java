package com.funix.swp490x.mrs.web.api.admin;

import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.mail.MailDeliveryException;
import com.funix.swp490x.mrs.mail.NotificationService;
import com.funix.swp490x.mrs.mail.SesIdentityException;
import com.funix.swp490x.mrs.mail.SesIdentityService;
import com.funix.swp490x.mrs.mail.SesIdentityService.Outcome;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.service.EmailPolicy;
import com.funix.swp490x.mrs.service.UserAccountService;
import com.funix.swp490x.mrs.service.UserAccountService.InitialCredentials;
import com.funix.swp490x.mrs.web.Messages;
import com.funix.swp490x.mrs.web.Routes;
import com.funix.swp490x.mrs.web.api.ApiError;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring REST CRUD for P-06a User Management (UC-06, UC-07).
 *
 * <pre>
 * GET    /api/admin/users              list (filters + page)
 * GET    /api/admin/users/{id}         read one
 * POST   /api/admin/users              create
 * PATCH  /api/admin/users/{id}         update role / status
 * DELETE /api/admin/users/{id}         soft-delete (deactivate)
 * POST   /api/admin/users/{id}/credentials/resend
 * POST   /api/admin/users/ses-recipients
 * </pre>
 */
@RestController
@RequestMapping(path = Routes.API_ADMIN_USERS, produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminUserRestController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserRestController.class);

    private final UserAccountService userAccountService;
    private final NotificationService notificationService;
    private final SesIdentityService sesIdentityService;
    private final ObjectProvider<JavaMailSender> mailSender;

    public AdminUserRestController(UserAccountService userAccountService,
            NotificationService notificationService,
            SesIdentityService sesIdentityService,
            ObjectProvider<JavaMailSender> mailSender) {
        this.userAccountService = userAccountService;
        this.notificationService = notificationService;
        this.sesIdentityService = sesIdentityService;
        this.mailSender = mailSender;
    }

    @GetMapping
    public UserPageResponse list(@RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page) {
        Page<User> result = userAccountService.search(role, status, q, page);
        return new UserPageResponse(
                result.map(UserResponse::from).getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast());
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return UserResponse.from(userAccountService.requireExisting(id));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@Valid @RequestBody CreateUserRequest request) {
        if (outboundMailEnabled()) {
            String address = request.email() == null ? "" : request.email().trim();
            try {
                if (!sesIdentityService.isVerified(address)) {
                    return ResponseEntity.unprocessableEntity()
                            .body(ApiError.of(Messages.SES_RECIPIENT_NOT_VERIFIED));
                }
            } catch (SesIdentityException e) {
                log.error("Could not confirm SES verification for {}", address, e);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(ApiError.of(Messages.SES_VERIFICATION_FAILED));
            }
        }

        User created = userAccountService.create(
                request.name(), request.email(), request.role(), request.password());

        try {
            notificationService.sendAccountCredentials(created, request.password());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new CredentialsDeliveryResponse(
                            UserResponse.from(created), Messages.USER_CREATED, true));
        } catch (MailDeliveryException e) {
            log.error("Credentials message to {} was not delivered", created.getEmail(), e);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new CredentialsDeliveryResponse(
                            UserResponse.from(created), Messages.USER_CREATED_EMAIL_FAILED, false));
        }
    }

    /**
     * Update role and/or status. Status {@code DEACTIVATED} soft-deletes;
     * {@code ACTIVE} reactivates.
     */
    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public UserResponse update(@PathVariable Long id,
            @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal MrsUserDetails actor) {
        User user = userAccountService.requireExisting(id);
        if (request.role() != null) {
            user = userAccountService.changeRole(id, request.role(), actor.getId());
        }
        if (request.status() != null) {
            if (request.status() == UserStatus.DEACTIVATED) {
                user = userAccountService.deactivate(id, actor.getId());
            } else if (request.status() == UserStatus.ACTIVE) {
                user = userAccountService.reactivate(id);
            }
        }
        return UserResponse.from(user);
    }

    /** Soft-delete (spec 4.9). Hard delete is intentionally not offered. */
    @DeleteMapping("/{id}")
    public ResponseEntity<UserResponse> delete(@PathVariable Long id,
            @AuthenticationPrincipal MrsUserDetails actor) {
        User deactivated = userAccountService.deactivate(id, actor.getId());
        return ResponseEntity.ok(UserResponse.from(deactivated));
    }

    @PostMapping("/{id}/credentials/resend")
    public ResponseEntity<CredentialsDeliveryResponse> resendCredentials(@PathVariable Long id) {
        InitialCredentials credentials = userAccountService.reissueInitialPassword(id);
        try {
            notificationService.sendAccountCredentials(credentials.user(), credentials.password());
            return ResponseEntity.ok(new CredentialsDeliveryResponse(
                    UserResponse.from(credentials.user()), Messages.CREDENTIALS_RESENT, true));
        } catch (MailDeliveryException e) {
            log.error("Credentials message to {} was not delivered",
                    credentials.user().getEmail(), e);
            return ResponseEntity.ok(new CredentialsDeliveryResponse(
                    UserResponse.from(credentials.user()),
                    Messages.CREDENTIALS_RESEND_FAILED,
                    false));
        }
    }

    /**
     * Declares a recipient with Amazon SES before create. Kept under the users
     * API because it gates UC-07 delivery in the SES sandbox.
     */
    @PostMapping(path = "/ses-recipients", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> prepareRecipient(
            @RequestBody Map<String, String> body) {
        String address = body.getOrDefault("email", "").trim();
        if (!EmailPolicy.isWellFormed(address)) {
            return ResponseEntity.unprocessableEntity().body(Map.of(
                    "status", "error",
                    "message", Messages.INVALID_EMAIL));
        }

        try {
            Outcome outcome = sesIdentityService.prepareRecipient(address);
            if (outcome == Outcome.ALREADY_VERIFIED) {
                return ResponseEntity.ok(Map.of(
                        "status", "already_verified",
                        "message", Messages.SES_ALREADY_VERIFIED));
            }
            return ResponseEntity.ok(Map.of(
                    "status", "sent",
                    "message", Messages.SES_VERIFICATION_SENT));
        } catch (SesIdentityException e) {
            log.error("SES identity preparation failed for {}", address, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "status", "error",
                    "message", Messages.SES_VERIFICATION_FAILED));
        }
    }

    private boolean outboundMailEnabled() {
        return mailSender.getIfAvailable() != null;
    }
}
