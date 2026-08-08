package com.funix.swp490x.mrs.web.api;

/**
 * JSON error body returned by admin REST endpoints.
 *
 * @param message human-readable explanation
 * @param violations optional password-policy failures (create only)
 */
public record ApiError(String message, java.util.List<String> violations) {

    public static ApiError of(String message) {
        return new ApiError(message, null);
    }

    public static ApiError of(String message, java.util.List<String> violations) {
        return new ApiError(message, violations);
    }
}
