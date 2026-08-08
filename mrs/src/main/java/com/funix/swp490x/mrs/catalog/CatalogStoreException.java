package com.funix.swp490x.mrs.catalog;

/** The staged catalog could not be listed or read. */
public class CatalogStoreException extends RuntimeException {

    public CatalogStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public CatalogStoreException(String message) {
        super(message);
    }
}
