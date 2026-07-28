package com.funix.swp490x.mrs.domain;

/**
 * Account lifecycle state. Accounts are never hard-deleted in v1.0 — an ADMIN
 * deactivates them instead (spec 4.9), which invalidates sessions within 60 s
 * (FT-01 AC-03).
 */
public enum UserStatus {
    ACTIVE,
    DEACTIVATED
}
