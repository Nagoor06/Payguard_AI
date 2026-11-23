package dev.nagoor.payguard.core;

public class ReviewConflictException extends RuntimeException {
    public ReviewConflictException() {
        super("This case is not pending human review.");
    }
}
