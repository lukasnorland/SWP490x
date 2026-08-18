package com.funix.swp490x.mrs.web.support;

import com.funix.swp490x.mrs.web.Messages;
import com.funix.swp490x.mrs.web.Routes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Tomcat rejects an oversize multipart with HTTP 413 before the import
 * controller runs. Map that to the same JSON the upload form already reads.
 */
@ControllerAdvice
public class MultipartUploadAdvice {

    private static final Logger log = LoggerFactory.getLogger(MultipartUploadAdvice.class);

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public Object tooLarge(Exception ex, HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        log.warn("Multipart upload rejected: {}", rootMessage(ex));
        if ("XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
                    "message", Messages.MEDIA_UPLOAD_TOO_LARGE,
                    "rejected", List.of()));
        }
        redirectAttributes.addFlashAttribute("flash", Messages.MEDIA_UPLOAD_TOO_LARGE);
        redirectAttributes.addFlashAttribute("flashVariant", "danger");
        return "redirect:" + Routes.ADMIN_IMPORT;
    }

    private static String rootMessage(Throwable ex) {
        Throwable current = ex;
        String last = ex.getMessage();
        int guard = 0;
        while (current.getCause() != null && current.getCause() != current && guard++ < 8) {
            current = current.getCause();
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                last = current.getClass().getSimpleName() + ": " + current.getMessage();
            }
        }
        return last == null ? ex.getClass().getSimpleName() : last;
    }
}
