package com.funix.swp490x.mrs.web.support;

import com.funix.swp490x.mrs.security.MrsUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Puts the signed-in account on every model so the shell (spec 4.0) can render
 * the top-bar identity and decide which navigation sections to show (spec 2.1).
 */
@ControllerAdvice
public class ShellModelAdvice {

    @ModelAttribute("currentUser")
    public MrsUserDetails currentUser(@AuthenticationPrincipal MrsUserDetails user) {
        return user;
    }
}
