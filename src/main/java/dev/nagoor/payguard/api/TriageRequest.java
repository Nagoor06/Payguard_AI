package dev.nagoor.payguard.api;

import dev.nagoor.payguard.core.GatewayCode;
import dev.nagoor.payguard.core.PaymentState;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TriageRequest(
    @NotNull PaymentState paymentState,
    @NotNull GatewayCode gatewayCode,
    @NotNull @Min(0) @Max(10) Integer retryCount,
    @NotBlank @Size(max = 400) String question
) {}
