package com.funix.swp490x.mrs.catalog;

/** A catalog object-store operation failed. */
public class CatalogStoreException extends RuntimeException {

    public CatalogStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public CatalogStoreException(String message) {
        super(message);
    }

    /** Wraps store failures, including credential refresh errors outside the SDK exception hierarchy. */
    public static CatalogStoreException of(String action, RuntimeException e) {
        if (e instanceof CatalogStoreException cse) {
            return cse;
        }
        return new CatalogStoreException(action + ": " + deepestMessage(e), e);
    }

    /**
     * Last non-blank message on the cause chain — the AWS CLI line when
     * {@code aws login} has expired, rather than the SDK wrapper around it.
     */
    public static String deepestMessage(Throwable e) {
        Throwable current = e;
        String last = e.getMessage();
        int guard = 0;
        while (current.getCause() != null && current.getCause() != current && guard++ < 8) {
            current = current.getCause();
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                last = current.getMessage();
            }
        }
        if (last == null || last.isBlank()) {
            return e.getClass().getSimpleName();
        }
        String trimmed = last.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }
}
