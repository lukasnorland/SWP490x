package com.funix.swp490x.mrs.service;

import java.util.List;

/** Account creation rejected because the password fails BR-12 (UC-07 E2). */
public class WeakPasswordException extends RuntimeException {

    private final List<String> violations;

    public WeakPasswordException(List<String> violations) {
        super("Password does not satisfy BR-12");
        this.violations = List.copyOf(violations);
    }

    /** The unmet rules, named as the screens name them. */
    public List<String> getViolations() {
        return violations;
    }
}
