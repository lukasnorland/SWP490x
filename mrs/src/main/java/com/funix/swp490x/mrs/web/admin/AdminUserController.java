package com.funix.swp490x.mrs.web.admin;

import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.mail.MailDeliveryException;
import com.funix.swp490x.mrs.mail.NotificationService;
import com.funix.swp490x.mrs.mail.SesIdentityException;
import com.funix.swp490x.mrs.mail.SesIdentityService;
import com.funix.swp490x.mrs.mail.SesIdentityService.Outcome;
import com.funix.swp490x.mrs.security.InitialPasswordGenerator;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.service.DuplicateEmailException;
import com.funix.swp490x.mrs.service.EmailPolicy;
import com.funix.swp490x.mrs.service.InvalidEmailException;
import com.funix.swp490x.mrs.service.InvalidSuccessorException;
import com.funix.swp490x.mrs.service.InvalidRoleAssignmentException;
import com.funix.swp490x.mrs.service.PlaylistSuccessorChoice;
import com.funix.swp490x.mrs.service.PlaylistSuccessorRequiredException;
import com.funix.swp490x.mrs.service.SelfModificationException;
import com.funix.swp490x.mrs.service.UserAccountService;
import com.funix.swp490x.mrs.service.UserAccountService.Deactivation;
import com.funix.swp490x.mrs.service.UserAccountService.InitialCredentials;
import com.funix.swp490x.mrs.service.UserAccountService.RoleChange;
import com.funix.swp490x.mrs.service.UserNotFoundException;
import com.funix.swp490x.mrs.service.UserView;
import com.funix.swp490x.mrs.service.WeakPasswordException;
import com.funix.swp490x.mrs.web.Messages;
import com.funix.swp490x.mrs.web.Routes;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
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
 * <p>Rejections of Create re-render the screen under the status code the SRS
 * names rather than redirecting, so the response says what happened and the
 * form keeps what ADMIN typed. Status and role changes redirect with a flash.
 */
@Controller
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

    private final UserAccountService userAccountService;
    private final NotificationService notificationService;
    private final SesIdentityService sesIdentityService;
    private final ObjectProvider<JavaMailSender> mailSender;

    public AdminUserController(UserAccountService userAccountService,
            NotificationService notificationService,
            SesIdentityService sesIdentityService,
            ObjectProvider<JavaMailSender> mailSender) {
        this.userAccountService = userAccountService;
        this.notificationService = notificationService;
        this.sesIdentityService = sesIdentityService;
        this.mailSender = mailSender;
    }

    @GetMapping(Routes.ADMIN_USERS)
    public String users(@RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        return renderScreen(model, role, status, q, page);
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

        if (outboundMailEnabled()) {
            String address = email == null ? "" : email.trim();
            try {
                if (!sesIdentityService.isVerified(address)) {
                    return reject(model, response, HttpStatus.UNPROCESSABLE_ENTITY,
                            Messages.SES_RECIPIENT_NOT_VERIFIED);
                }
            } catch (SesIdentityException e) {
                log.error("Could not confirm SES verification for {}", address, e);
                return reject(model, response, HttpStatus.BAD_GATEWAY, Messages.SES_VERIFICATION_FAILED);
            }
        }

        UserView created;
        try {
            created = userAccountService.create(name, email, role, password);
        } catch (InvalidRoleAssignmentException e) {
            return reject(model, response, HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
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
            notificationService.sendAccountCredentials(
                    created.username(), created.email(), created.role().getDisplayName(), password);
            flash(redirectAttributes, "success", Messages.USER_CREATED);
        } catch (MailDeliveryException e) {
            reportUndelivered("Credentials", created.email(), e);
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
    public String resendCredentials(@PathVariable Long id,
            @AuthenticationPrincipal MrsUserDetails actor,
            RedirectAttributes redirectAttributes) {
        try {
            InitialCredentials credentials =
                    userAccountService.reissueInitialPassword(id, actor.getId());
            try {
                UserView account = credentials.user();
                notificationService.sendAccountCredentials(
                        account.username(), account.email(), account.role().getDisplayName(),
                        credentials.password());
                flash(redirectAttributes, "success", Messages.CREDENTIALS_RESENT);
            } catch (MailDeliveryException e) {
                reportUndelivered("Credentials", credentials.user().email(), e);
                flash(redirectAttributes, "warning", Messages.CREDENTIALS_RESEND_FAILED);
            }
        } catch (SelfModificationException e) {
            flash(redirectAttributes, "danger", Messages.SELF_MODIFICATION_FORBIDDEN);
        }
        return "redirect:" + Routes.ADMIN_USERS;
    }

    /**
     * Spec 4.9 — soft-delete; open sessions end on the next request (FT-01 AC-03).
     * The holder is told by email once the change has committed; as with Create,
     * a failed delivery leaves the change in place and warns ADMIN instead.
     */
    @PostMapping(Routes.ADMIN_USER_DEACTIVATE)
    public String deactivate(@PathVariable Long id,
            @AuthenticationPrincipal MrsUserDetails actor,
            RedirectAttributes redirectAttributes) {
        Deactivation outcome;
        try {
            outcome = userAccountService.deactivate(id, actor.getId());
        } catch (SelfModificationException e) {
            flash(redirectAttributes, "danger", Messages.SELF_MODIFICATION_FORBIDDEN);
            return "redirect:" + Routes.ADMIN_USERS;
        } catch (PlaylistSuccessorRequiredException e) {
            return "redirect:" + Routes.ADMIN_USERS + "/" + id + "/reassign?intent=deactivate";
        }

        return finishDeactivation(outcome, redirectAttributes);
    }

    @GetMapping(Routes.ADMIN_USER_REASSIGN)
    public String reassignForm(@PathVariable Long id,
            @RequestParam String intent,
            @AuthenticationPrincipal MrsUserDetails actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!"deactivate".equals(intent) && !"demote".equals(intent)) {
            return "redirect:" + Routes.ADMIN_USERS;
        }
        UserView account;
        try {
            account = userAccountService.get(id);
        } catch (UserNotFoundException e) {
            return "redirect:" + Routes.ADMIN_USERS;
        }
        List<PlaylistSuccessorChoice> choices = userAccountService.successorChoices(id);
        if (choices.isEmpty()) {
            return "redirect:" + Routes.ADMIN_USERS;
        }
        model.addAttribute("pageTitle", "Reassign playlists");
        model.addAttribute("activeNav", "admin-users");
        model.addAttribute("breadcrumbParent", "User Management");
        model.addAttribute("breadcrumbParentUrl", Routes.ADMIN_USERS);
        model.addAttribute("account", account);
        model.addAttribute("intent", intent);
        model.addAttribute("choices", choices);
        model.addAttribute("adminId", actor.getId());
        model.addAttribute("adminName", actor.getDisplayName());
        return "admin/reassign";
    }

    @PostMapping(Routes.ADMIN_USER_REASSIGN)
    public String reassign(@PathVariable Long id,
            @RequestParam String intent,
            @RequestParam Map<String, String> params,
            @AuthenticationPrincipal MrsUserDetails actor,
            RedirectAttributes redirectAttributes) {
        Map<Long, Long> successors = parseSuccessors(params);
        try {
            if ("deactivate".equals(intent)) {
                return finishDeactivation(
                        userAccountService.deactivate(id, actor.getId(), successors),
                        redirectAttributes);
            }
            if ("demote".equals(intent)) {
                return finishRoleChange(
                        userAccountService.changeRole(id, Role.CUSTOMER, actor.getId(), successors),
                        redirectAttributes);
            }
        } catch (SelfModificationException e) {
            flash(redirectAttributes, "danger", Messages.SELF_MODIFICATION_FORBIDDEN);
        } catch (PlaylistSuccessorRequiredException | InvalidSuccessorException e) {
            flash(redirectAttributes, "warning", Messages.SUCCESSOR_REQUIRED);
            return "redirect:" + Routes.ADMIN_USERS + "/" + id + "/reassign?intent=" + intent;
        } catch (InvalidRoleAssignmentException e) {
            flash(redirectAttributes, "danger", e.getMessage());
        }
        return "redirect:" + Routes.ADMIN_USERS;
    }

    @PostMapping(Routes.ADMIN_USER_REACTIVATE)
    public String reactivate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        userAccountService.reactivate(id);
        flash(redirectAttributes, "success", Messages.USER_REACTIVATED);
        return "redirect:" + Routes.ADMIN_USERS;
    }

    /**
     * UC-06 — Content Designer or Customer only; expires the target's sessions
     * and tells the holder which role they moved from and to. A no-op change
     * (same role again) sends nothing.
     */
    @PostMapping(Routes.ADMIN_USER_ROLE)
    public String changeRole(@PathVariable Long id,
            @RequestParam Role role,
            @AuthenticationPrincipal MrsUserDetails actor,
            RedirectAttributes redirectAttributes) {
        RoleChange outcome;
        try {
            outcome = userAccountService.changeRole(id, role, actor.getId());
        } catch (SelfModificationException e) {
            flash(redirectAttributes, "danger", Messages.SELF_MODIFICATION_FORBIDDEN);
            return "redirect:" + Routes.ADMIN_USERS;
        } catch (PlaylistSuccessorRequiredException e) {
            return "redirect:" + Routes.ADMIN_USERS + "/" + id + "/reassign?intent=demote";
        } catch (InvalidRoleAssignmentException e) {
            flash(redirectAttributes, "danger", e.getMessage());
            return "redirect:" + Routes.ADMIN_USERS;
        }

        if (!outcome.changed()) {
            flash(redirectAttributes, "success", Messages.USER_ROLE_CHANGED);
            return "redirect:" + Routes.ADMIN_USERS;
        }
        return finishRoleChange(outcome, redirectAttributes);
    }

    private String finishDeactivation(Deactivation outcome, RedirectAttributes redirectAttributes) {
        if (!outcome.changed()) {
            flash(redirectAttributes, "success", Messages.USER_DEACTIVATED);
            return "redirect:" + Routes.ADMIN_USERS;
        }
        UserView account = outcome.user();
        String transferNote = outcome.transferredPlaylists() == 0
                ? ""
                : " " + Messages.playlistsTransferred(outcome.transferredPlaylists());
        try {
            notificationService.sendAccountDeactivated(account.username(), account.email(),
                    outcome.transferredPlaylists());
            flash(redirectAttributes, "success", Messages.USER_DEACTIVATED + transferNote);
        } catch (MailDeliveryException e) {
            reportUndelivered("Deactivation", account.email(), e);
            flash(redirectAttributes, "warning",
                    Messages.USER_DEACTIVATED_EMAIL_FAILED + transferNote);
        }
        return "redirect:" + Routes.ADMIN_USERS;
    }

    private String finishRoleChange(RoleChange outcome, RedirectAttributes redirectAttributes) {
        UserView account = outcome.user();
        String transferNote = outcome.transferredPlaylists() == 0
                ? ""
                : " " + Messages.playlistsTransferred(outcome.transferredPlaylists());
        try {
            notificationService.sendRoleChanged(account.username(), account.email(),
                    outcome.previousRole().getDisplayName(), account.role().getDisplayName(),
                    outcome.transferredPlaylists());
            flash(redirectAttributes, "success", Messages.USER_ROLE_CHANGED + transferNote);
        } catch (MailDeliveryException e) {
            reportUndelivered("Role change", account.email(), e);
            flash(redirectAttributes, "warning",
                    Messages.USER_ROLE_CHANGED_EMAIL_FAILED + transferNote);
        }
        return "redirect:" + Routes.ADMIN_USERS;
    }

    private static Map<Long, Long> parseSuccessors(Map<String, String> params) {
        Map<Long, Long> successors = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith("successor[") || !key.endsWith("]")) {
                continue;
            }
            try {
                Long playlistId = Long.valueOf(key.substring("successor[".length(), key.length() - 1));
                successors.put(playlistId, Long.valueOf(entry.getValue()));
            } catch (NumberFormatException ignored) {
                // Skip a crafted field rather than failing the whole form.
            }
        }
        return successors;
    }

    private String renderScreen(Model model, Role roleFilter, UserStatus statusFilter,
            String query, int page) {
        Page<UserView> users = userAccountService.search(roleFilter, statusFilter, query, page);
        model.addAttribute("pageTitle", "User Management");
        model.addAttribute("activeNav", "admin-users");
        model.addAttribute("users", users);
        model.addAttribute("filterRole", roleFilter);
        model.addAttribute("filterStatus", statusFilter);
        model.addAttribute("filterQuery", query == null ? "" : query);
        // Create, role-change, and the role filter: Content Designer / Customer only.
        // ADMIN accounts are also omitted from the list query itself.
        model.addAttribute("filterRoles", UserAccountService.ASSIGNABLE_ROLES);
        model.addAttribute("filterStatuses", UserStatus.values());
        model.addAttribute("assignableRoles", UserAccountService.ASSIGNABLE_ROLES);
        // Offered as the default so the flow works without JavaScript; the
        // generate button replaces it in the browser.
        model.addAttribute("suggestedPassword", InitialPasswordGenerator.generate());
        // When real SMTP is on, Create account is gated on SES recipient
        // verification (UI + server). Logging transport leaves the gate open.
        model.addAttribute("requireSesRecipient", outboundMailEnabled());
        return "admin/users";
    }

    private boolean outboundMailEnabled() {
        return mailSender.getIfAvailable() != null;
    }

    private String reject(Model model, HttpServletResponse response, HttpStatus status,
            String message) {

        response.setStatus(status.value());
        if (message != null) {
            model.addAttribute("flash", message);
            model.addAttribute("flashVariant", "danger");
        }
        model.addAttribute("reopenCreateForm", true);
        return renderScreen(model, null, null, null, 0);
    }

    /**
     * BR-10 puts this in the audit log. Nothing writes that table yet, so the
     * application log carries it while ADMIN is told on screen.
     */
    private void reportUndelivered(String kind, String email, MailDeliveryException cause) {
        log.error("{} message to {} was not delivered", kind, email, cause);
    }

    private void flash(RedirectAttributes redirectAttributes, String variant, String message) {
        redirectAttributes.addFlashAttribute("flash", message);
        redirectAttributes.addFlashAttribute("flashVariant", variant);
    }
}
