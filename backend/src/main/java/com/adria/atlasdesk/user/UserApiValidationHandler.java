package com.adria.atlasdesk.user;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = UserAdminController.class)
public class UserApiValidationHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ValidationError> handleConstraintViolation() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ValidationError("invalid_request"));
    }

    public record ValidationError(String error) {
    }
}
