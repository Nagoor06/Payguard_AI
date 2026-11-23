package dev.nagoor.payguard.core;

public enum DecisionCategory {
    RESOLVED, WAIT_AND_RECHECK, RETRY_CANDIDATE, DO_NOT_RETRY, HUMAN_REVIEW
}
