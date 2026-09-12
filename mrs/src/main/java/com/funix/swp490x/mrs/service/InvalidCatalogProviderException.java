package com.funix.swp490x.mrs.service;

/**
 * The submitted provider name or slug failed validation.
 */
public class InvalidCatalogProviderException extends RuntimeException {

    public InvalidCatalogProviderException(String message) {
        super(message);
    }
}
