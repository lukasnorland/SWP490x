package com.funix.swp490x.mrs.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funix.swp490x.mrs.config.SecurityConfig;
import com.funix.swp490x.mrs.config.WebConfig;
import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.CorrelationIdFilter;
import com.funix.swp490x.mrs.security.LoginAttemptService;
import com.funix.swp490x.mrs.security.LoginFailureHandler;
import com.funix.swp490x.mrs.security.LoginSuccessHandler;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.security.MrsUserDetailsService;
import com.funix.swp490x.mrs.security.PasswordResetTokenService;
import com.funix.swp490x.mrs.security.RequestRateLimiter;
import com.funix.swp490x.mrs.web.support.CorrelationIdAdvice;
import com.funix.swp490x.mrs.web.support.ShellModelAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * P-09 500 card: SYS_001 plus the request id as an opaque reference (TDS §8.1 / §8.4).
 *
 * <p>The view is rendered directly so this slice does not depend on the
 * container's error dispatch; {@link CorrelationIdFilter} still runs on the
 * way in and leaves the attribute the template reads.
 */
@WebMvcTest(controllers = FiveHundredView.class)
@Import({SecurityConfig.class, WebConfig.class, ShellModelAdvice.class, CorrelationIdAdvice.class,
        LoginSuccessHandler.class, LoginFailureHandler.class, LoginAttemptService.class,
        MrsUserDetailsService.class, PasswordResetTokenService.class, RequestRateLimiter.class})
class ServerErrorPageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    @Test
    void fiveHundredCardShowsSys001AndTheRequestId() throws Exception {
        mockMvc.perform(get("/__test__/error-500")
                        .with(user(principal()))
                        .header(CorrelationIdFilter.HEADER, "trace-500"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(Messages.SERVER_ERROR)))
                .andExpect(content().string(containsString("Reference:")))
                .andExpect(content().string(containsString("trace-500")));
    }

    private static MrsUserDetails principal() {
        User user = new User();
        user.setId(1L);
        user.setUsername("Dana Designer");
        user.setEmail("dana@mrs.local");
        user.setPasswordHash("{noop}irrelevant");
        user.setRole(Role.CONTENT_DESIGNER);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(false);
        return new MrsUserDetails(user, true);
    }
}

@Controller
class FiveHundredView {

    @GetMapping("/__test__/error-500")
    String page() {
        return "error/500";
    }
}
