package dev.nagoor.payguard.core;

public class UnsafeInputException extends RuntimeException {
    public UnsafeInputException() {
        super("Remove email addresses and payment card numbers; use synthetic diagnostic data only.");
    }
}
