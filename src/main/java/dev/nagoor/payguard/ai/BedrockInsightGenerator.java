package dev.nagoor.payguard.ai;

import dev.nagoor.payguard.api.TriageRequest;
import dev.nagoor.payguard.core.PaymentDecision;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Objects;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "bedrock")
public class BedrockInsightGenerator implements InsightGenerator {
    private final BedrockRuntimeClient client;
    private final String modelId;
    private final ExplanationGuard guard;
    private final MeterRegistry metrics;

    public BedrockInsightGenerator(
            @Value("${app.ai.region}") String region,
            @Value("${app.ai.model-id}") String modelId,
            ExplanationGuard guard, MeterRegistry metrics) {
        this.guard = guard;
        this.metrics = metrics;
        this.modelId = modelId;
        this.client = BedrockRuntimeClient.builder()
            .region(Region.of(region))
            .overrideConfiguration(ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(12))
                .apiCallAttemptTimeout(Duration.ofSeconds(10))
                .build())
            .build();
    }

    @Override
    public Insight explain(TriageRequest request, PaymentDecision decision) {
        String prompt = PromptBuilder.build(request, decision);
        var converseRequest = ConverseRequest.builder()
            .modelId(modelId)
            .messages(Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(prompt))
                .build())
            .inferenceConfig(InferenceConfiguration.builder()
                .maxTokens(140)
                .temperature(0.0f)
                .build())
            .build();
        try {
            var response = client.converse(converseRequest);
            String output = response.output().message().content().stream()
                .map(ContentBlock::text)
                .filter(Objects::nonNull)
                .reduce((first, second) -> first + " " + second)
                .orElse("");
            if (!guard.acceptable(output)) {
                metrics.counter("payguard.ai.responses", "source", "output_filtered").increment();
                return new Insight(decision.nextStep(), "POLICY_FALLBACK");
            }
            metrics.counter("payguard.ai.responses", "source", "bedrock").increment();
            return new Insight(output.strip(), "AWS_BEDROCK");
        } catch (RuntimeException ex) {
            // Also handle unexpected response shapes and SDK runtime errors.
            // No prompts, credentials or raw cloud errors in user-facing output or metrics.
            metrics.counter("payguard.ai.responses", "source", "api_fallback").increment();
            return new Insight(decision.nextStep(), "POLICY_FALLBACK");
        }
    }

    @PreDestroy
    void shutdown() {
        client.close();
    }
}
