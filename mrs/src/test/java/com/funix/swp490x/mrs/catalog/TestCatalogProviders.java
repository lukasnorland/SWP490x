package com.funix.swp490x.mrs.catalog;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.funix.swp490x.mrs.service.CatalogProviderService;
import java.util.List;
import java.util.Locale;

/** Registered providers matching the V1 seed, for unit tests that no longer read CatalogProperties. */
final class TestCatalogProviders {

    private TestCatalogProviders() {
    }

    static CatalogProviderService stub() {
        CatalogProviderService providers = mock(CatalogProviderService.class);
        given(providers.registeredNames()).willReturn(List.of("EpidemicSound", "NCS", "OneOff"));
        given(providers.isRegistered(anyString())).willAnswer(invocation -> {
            String name = invocation.getArgument(0);
            return name != null && slug(name) != null;
        });
        given(providers.slugFor(anyString())).willAnswer(invocation -> slug(invocation.getArgument(0)));
        return providers;
    }

    private static String slug(String name) {
        if (name == null) {
            return null;
        }
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "epidemicsound" -> "epidemic";
            case "ncs" -> "ncs";
            case "oneoff" -> "one-off";
            default -> null;
        };
    }
}
