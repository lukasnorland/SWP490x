package com.funix.swp490x.mrs.web.admin;

import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.mail.MailDeliveryException;
import com.funix.swp490x.mrs.mail.NotificationService;
import com.funix.swp490x.mrs.mail.SesIdentityException;
import com.funix.swp490x.mrs.mail.SesIdentityService;
import com.funix.swp490x.mrs.mail.SesIdentityService.Outcome;
import com.funix.swp490x.mrs.security.InitialPasswordGenerator;
import com.funix.swp490x.mrs.service.DuplicateEmailException;
import com.funix.swp490x.mrs.service.EmailPolicy;
import com.funix.swp490x.mrs.service.InvalidEmailException;
import com.funix.swp490x.mrs.service.UserAccountService;
import com.funix.swp490x.mrs.service.UserAccountService.InitialCredentials;
import com.funix.swp490x.mrs.service.WeakPasswordException;
import com.funix.swp490x.mrs.web.Messages;
import com.funix.swp490x.mrs.web.Routes;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * P-06a — User Management (UC-06, UC-07).
 *
 * <p>Rejections re-render the screen under the status code the SRS names rather
 * than redirecting, so the response says what happened and the form keeps what
 * ADMIN typed.
 */
@Controller
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

    /** UC-07 assigns one of these; ADMIN accounts are not created from a screen. */
    private static final List<Role> ASSIGNABLE_ROLES = List.of(Role.CONTENT_DESIGNER, Role.CUSTOMER);

    private final UserAccountService userAccountService;
    private final NotificationService notificationService;
    private final SesIdentityService sesIdentityService;

    public AdminUserController(UserAccountService userAccountService,
            NotificationService notificationService,
            SesIdentityService sesIdentityService) {
        this.userAccountService = userAccountService;
        this.notificationService = notificationService;
        this.sesIdentityService = sesIdentityService;
    }

    @GetMapping(Routes.ADMIN_USERS)
    public String users(Model model) {
        return renderScreen(model);
    }

    /** UC-07 — the only path to a new account (BR-01). */
    @PostMapping(Routes.ADMIN_USERS)
    public String create(@RequestParam String name,
            @RequestParam String email,
            @RequestParam Role role,
            @RequestParam String password,
            Model model,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {

        model.addAttribute("submittedName", name);
        model.addAttribute("submittedEmail", email);
        model.addAttribute("submittedRole", role);

        if (!ASSIGNABLE_ROLES.contains(role)) {
            return reject(model, response, HttpStatus.UNPROCESSABLE_ENTITY,
                    "New accounts can be assigned the Content Designer or Customer role only.");
        }

        User created;
        try {
            created = userAccountService.create(name, email, role, password);
        } catch (InvalidEmailException e) {
            return reject(model, response, HttpStatus.UNPROCESSABLE_ENTITY, Messages.INVALID_EMAIL);
        } catch (DuplicateEmailException e) {
            return reject(model, response, HttpStatus.CONFLICT, Messages.DUPLICATE_EMAIL);
        } catch (WeakPasswordException e) {
            model.addAttribute("violations", e.getViolations());
            return reject(model, response, HttpStatus.UNPROCESSABLE_ENTITY, null);
        }

        // E3: the account stands even when the message does not go out, so
        // delivery is attempted only once create() has committed.
        try {
            notificationService.sendAccountCredentials(created, password);
            flash(redirectAttributes, "success", Messages.USER_CREATED);
        } catch (MailDeliveryException e) {
            reportUndelivered(created.getEmail(), e);
            flash(redirectAttributes, "warning", Messages.USER_CREATED_EMAIL_FAILED);
        }

        return "redirect:" + Routes.ADMIN_USERS;
    }

    /**
     * Declares the typed address with Amazon SES before the account exists.
     *
     * <p>Returns JSON so the create dialog can stay open with the other fields
     * intact — this step is deliberately separate from Create account.
     */
    @PostMapping(path = Routes.ADMIN_USER_PREPARE_RECIPIENT,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, String>> prepareRecipient(@RequestParam String email) {
        String address = email == null ? "" : email.trim();
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

    /** UC-07 E3 — send the credentials message again after a failed delivery. */
    @PostMapping(Routes.ADMIN_USER_RESEND)
    public String resendCredentials(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        InitialCredentials credentials = userAccountService.reissueInitialPassword(id);
        try {
            notificationService.sendAccountCredentials(credentials.user(), credentials.password());
            flash(redirectAttributes, "success", Messages.CREDENTIALS_RESENT);
        } catch (MailDeliveryException e) {
            reportUndelivered(credentials.user().getEmail(), e);
            flash(redirectAttributes, "warning", Messages.CREDENTIALS_RESEND_FAILED);
        }
        return "redirect:" + Routes.ADMIN_USERS;
    }

    private String renderScreen(Model model) {
        model.addAttribute("pageTitle", "User Management");
        model.addAttribute("activeNav", "admin-users");
        model.addAttribute("users", userAccountService.listAll());
        model.addAttribute("assignableRoles", ASSIGNABLE_ROLES);
        // Offered as the default so the flow works without JavaScript; the
        // generate button replaces it in the browser.
        model.addAttribute("suggestedPassword", InitialPasswordGenerator.generate());
        return "admin/users";
    }

    private String reject(Model model, HttpServletResponse response, HttpStatus status,
            String message) {

        response.setStatus(status.value());
        if (message != null) {
            model.addAttribute("flash", message);
            model.addAttribute("flashVariant", "danger");
        }
        model.addAttribute("reopenCreateForm", true);
        return renderScreen(model);
    }

    /**
     * BR-10 puts this in the audit log. Nothing writes that table yet, so the
     * application log carries it while ADMIN is told on screen.
     */
    private void reportUndelivered(String email, MailDeliveryException cause) {
        log.error("Credentials message to {} was not delivered", email, cause);
    }

    private void flash(RedirectAttributes redirectAttributes, String variant, String message) {
        redirectAttributes.addFlashAttribute("flash", message);
        redirectAttributes.addFlashAttribute("flashVariant", variant);
    }
}
