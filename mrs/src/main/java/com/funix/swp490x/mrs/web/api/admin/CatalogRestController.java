package com.funix.swp490x.mrs.web.api.admin;

import com.funix.swp490x.mrs.web.Routes;
import com.funix.swp490x.mrs.web.api.PageResponse;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** P-06b catalog REST stub (songs + tags). */
@RestController
@RequestMapping(path = Routes.API_ADMIN_CATALOG, produces = MediaType.APPLICATION_JSON_VALUE)
public class CatalogRestController {

    @GetMapping("/songs")
    public PageResponse<Map<String, Object>> songs(@RequestParam(defaultValue = "0") int page) {
        return PageResponse.empty(page, 20);
    }

    @GetMapping("/tags")
    public PageResponse<Map<String, Object>> tags(@RequestParam(defaultValue = "0") int page) {
        return PageResponse.empty(page, 20);
    }
}
