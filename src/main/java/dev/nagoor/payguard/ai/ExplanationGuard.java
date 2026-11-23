package dev.nagoor.payguard.ai;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Conservative output filter; NOT a replacement for human review or full PII/DLP tooling. */
@Component
public final class ExplanationGuard {
    private static final Pattern CARD = Pattern.compile("(?<!\\d)(?:\\d[ -]?){12,18}\\d(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern UNSAFE_CLAIM = Pattern.compile(
        "(?i)\\b(?:I|we|the system)\\s+(?:have\\s+|has\\s+)?(?:charged|refunded|retried|transferred|captured)\\b"
      + "|\\b(?:charge|refund|transfer|capture|retry)\\s+(?:this\\s+|the\\s+)?(?:payment|card|transaction)\\s+now\\b");

    public boolean acceptable(String explanation) {
        return explanation != null && !explanation.isBlank() && explanation.length() <= 500
            && !EMAIL.matcher(explanation).find() && !CARD.matcher(explanation).find()
            && !UNSAFE_CLAIM.matcher(explanation).find();
    }
}
