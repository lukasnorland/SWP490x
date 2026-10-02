package com.funix.swp490x.mrs.security;

import java.util.List;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/** Marks account sessions expired; the next request invalidates them (FT-01 AC-03). */
@Service
public class SessionInvalidationService {

    private final SessionRegistry sessionRegistry;

    public SessionInvalidationService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /** Expire every non-expired session whose principal authenticates as {@code email}. */
    public void invalidateSessionsForEmail(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String target = email.trim();
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (!(principal instanceof UserDetails details)) {
                continue;
            }
            if (!target.equalsIgnoreCase(details.getUsername())) {
                continue;
            }
            List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
            for (SessionInformation session : sessions) {
                session.expireNow();
            }
        }
    }
}
