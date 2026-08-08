package com.funix.swp490x.mrs.web.api;

import com.funix.swp490x.mrs.service.DuplicateEmailException;
import com.funix.swp490x.mrs.service.InvalidEmailException;
import com.funix.swp490x.mrs.service.InvalidRoleAssignmentException;
import com.funix.swp490x.mrs.service.SelfModificationException;
import com.funix.swp490x.mrs.service.UserNotFoundException;
import com.funix.swp490x.mrs.service.WeakPasswordException;
import com.funix.swp490x.mrs.web.Messages;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain failures from {@code /api/**} onto HTTP status + {@link ApiError}. */
@RestControllerAdvice(basePackages = "com.funix.swp490x.mrs.web.api")
public class ApiExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> notFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiError> duplicate(DuplicateEmailException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(Messages.DUPLICATE_EMAIL));
    }

    @ExceptionHandler(InvalidEmailException.class)
    public ResponseEntity<ApiError> invalidEmail(InvalidEmailException ex) {
        return ResponseEntity.unprocessableEntity().body(ApiError.of(Messages.INVALID_EMAIL));
    }

    @ExceptionHandler(WeakPasswordException.class)
    public ResponseEntity<ApiError> weakPassword(WeakPasswordException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(ApiError.of("This password was not accepted", ex.getViolations()));
    }

    @ExceptionHandler({InvalidRoleAssignmentException.class, SelfModificationException.class})
    public ResponseEntity<ApiError> unprocessable(RuntimeException ex) {
        String message = ex instanceof SelfModificationException
                ? Messages.SELF_MODIFICATION_FORBIDDEN
                : ex.getMessage();
        return ResponseEntity.unprocessableEntity().body(ApiError.of(message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Request body failed validation.";
        }
        return ResponseEntity.unprocessableEntity().body(ApiError.of(message));
    }
}
