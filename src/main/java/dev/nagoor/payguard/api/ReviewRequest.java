package dev.nagoor.payguard.api;

import dev.nagoor.payguard.core.ReviewAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Pseudonymous operator reference only; no names, emails or free-text notes. */
public record ReviewRequest(
    @NotNull ReviewAction action,
    @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{3,32}") String reviewerRef
) {}
