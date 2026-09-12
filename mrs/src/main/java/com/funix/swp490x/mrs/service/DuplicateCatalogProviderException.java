package com.funix.swp490x.mrs.service;

/**
 * A new catalog provider reused a name or slug that is already registered.
 */
public class DuplicateCatalogProviderException extends RuntimeException {

    public DuplicateCatalogProviderException(String message) {
        super(message);
    }
}
