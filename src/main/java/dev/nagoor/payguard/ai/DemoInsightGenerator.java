package dev.nagoor.payguard.ai;

import dev.nagoor.payguard.api.TriageRequest;
import dev.nagoor.payguard.core.PaymentDecision;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Offline deterministic mode; no calls to Bedrock or claims of AI inference. */
@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "demo", matchIfMissing = true)
public class DemoInsightGenerator implements InsightGenerator {
    @Override
    public Insight explain(TriageRequest request, PaymentDecision decision) {
        return new Insight("Demo explanation: " + decision.nextStep(), "DEMO_RULES");
    }
}
