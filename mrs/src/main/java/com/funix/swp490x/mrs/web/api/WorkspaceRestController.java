package com.funix.swp490x.mrs.web.api;

import com.funix.swp490x.mrs.web.Routes;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** P-04 shared workspace REST stub. */
@RestController
@RequestMapping(path = Routes.API_WORKSPACE, produces = MediaType.APPLICATION_JSON_VALUE)
public class WorkspaceRestController {

    @GetMapping
    public PageResponse<Map<String, Object>> list(@RequestParam(defaultValue = "0") int page) {
        return PageResponse.empty(page, 12);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Published playlist " + id + " is not available yet");
    }
}
