package com.funix.swp490x.mrs.web.api.admin;

import com.funix.swp490x.mrs.web.Routes;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** P-06c import REST stub. */
@RestController
@RequestMapping(path = Routes.API_ADMIN_IMPORT, produces = MediaType.APPLICATION_JSON_VALUE)
public class ImportRestController {

    @GetMapping
    public Map<String, Object> status() {
        return Map.of(
                "status", "idle",
                "message", "Catalog import API is scaffolded; upload flow arrives with the import slice.");
    }
}
