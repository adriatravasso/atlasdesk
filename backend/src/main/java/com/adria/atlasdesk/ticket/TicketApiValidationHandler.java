package com.adria.atlasdesk.ticket;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TicketController.class)
public class TicketApiValidationHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ValidationError> handleConstraintViolation() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ValidationError("invalid_request"));
    }

    @ExceptionHandler({TicketConflictException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<ValidationError> handleConflict(RuntimeException exception) {
        String detail = exception instanceof TicketConflictException
                ? exception.getMessage()
                : "Ticket was changed by another request; reload it and retry";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ValidationError("conflict", detail));
    }

    public record ValidationError(String error, String detail) {
        public ValidationError(String error) {
            this(error, null);
        }
    }
}
