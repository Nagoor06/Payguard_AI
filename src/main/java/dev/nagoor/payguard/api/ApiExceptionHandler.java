package dev.nagoor.payguard.api;

import dev.nagoor.payguard.core.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(UnsafeInputException.class)
    public ResponseEntity<ProblemDetail> unsafe(UnsafeInputException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }
    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ResponseEntity<ProblemDetail> invalidKey(InvalidIdempotencyKeyException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    @ExceptionHandler({IdempotencyConflictException.class, ReviewConflictException.class})
    public ResponseEntity<ProblemDetail> conflict(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }
    @ExceptionHandler(CaseNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(CaseNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }
    @ExceptionHandler({MethodArgumentNotValidException.class,
                       HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ProblemDetail> badRequest(Exception ex) {
        // Do not echo submitted prompts, payment identifiers, or raw binding errors.
        return problem(HttpStatus.BAD_REQUEST, "Invalid or incomplete request body or headers.");
    }
    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, message));
    }
}
