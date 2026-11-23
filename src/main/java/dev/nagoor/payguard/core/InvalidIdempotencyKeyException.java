package dev.nagoor.payguard.core;

public class InvalidIdempotencyKeyException extends RuntimeException {
    public InvalidIdempotencyKeyException() {
        super("Idempotency-Key must be 12-128 characters using letters, numbers, dot, underscore or hyphen.");
    }
}
