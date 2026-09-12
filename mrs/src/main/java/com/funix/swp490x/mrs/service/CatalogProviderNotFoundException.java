package com.funix.swp490x.mrs.service;

public class CatalogProviderNotFoundException extends RuntimeException {

    public CatalogProviderNotFoundException(Long id) {
        super("Catalog provider " + id + " is not registered");
    }
}
