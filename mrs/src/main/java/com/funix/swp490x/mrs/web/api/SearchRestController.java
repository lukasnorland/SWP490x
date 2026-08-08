package com.funix.swp490x.mrs.web.api;

import com.funix.swp490x.mrs.web.Routes;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * P-02 Search & Recommendation REST stub. Domain search lands here later;
 * today the resource returns an empty result set so the API surface is fixed.
 */
@RestController
@RequestMapping(path = Routes.API_SEARCH, produces = MediaType.APPLICATION_JSON_VALUE)
public class SearchRestController {

    @GetMapping
    public PageResponse<Map<String, Object>> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page) {
        return PageResponse.empty(page, 20);
    }
}
