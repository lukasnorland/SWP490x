package com.funix.swp490x.mrs.domain;

/** Accounts are deactivated rather than hard-deleted; active sessions are revoked (FT-01 AC-03). */
public enum UserStatus {
    ACTIVE,
    DEACTIVATED
}
