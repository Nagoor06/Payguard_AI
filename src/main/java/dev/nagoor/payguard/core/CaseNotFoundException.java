package dev.nagoor.payguard.core;

public class CaseNotFoundException extends RuntimeException {
    public CaseNotFoundException() {
        super("Triage case not found.");
    }
}
