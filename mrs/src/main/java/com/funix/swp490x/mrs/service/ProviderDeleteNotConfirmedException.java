package com.funix.swp490x.mrs.service;

/**
 * Cascade delete of a catalog provider was refused because the typed name
 * did not match.
 */
public class ProviderDeleteNotConfirmedException extends RuntimeException {

    public ProviderDeleteNotConfirmedException() {
        super("Type the provider name to confirm deletion");
    }
}
