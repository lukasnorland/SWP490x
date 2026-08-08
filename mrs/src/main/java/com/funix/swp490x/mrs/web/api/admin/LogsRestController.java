package com.funix.swp490x.mrs.web.api.admin;

import com.funix.swp490x.mrs.web.Routes;
import com.funix.swp490x.mrs.web.api.PageResponse;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** P-06e audit / recommendation log REST stub. */
@RestController
@RequestMapping(path = Routes.API_ADMIN_LOGS, produces = MediaType.APPLICATION_JSON_VALUE)
public class LogsRestController {

    @GetMapping("/audit")
    public PageResponse<Map<String, Object>> audit(@RequestParam(defaultValue = "0") int page) {
        return PageResponse.empty(page, 20);
    }

    @GetMapping("/recommendations")
    public PageResponse<Map<String, Object>> recommendations(
            @RequestParam(defaultValue = "0") int page) {
        return PageResponse.empty(page, 20);
    }
}
