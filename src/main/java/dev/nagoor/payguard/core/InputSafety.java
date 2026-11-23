package dev.nagoor.payguard.core;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** A simple guard, not a full DLP/PCI-compliance solution. */
@Component
public final class InputSafety {
    private static final Pattern EMAIL = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern CARD_LIKE = Pattern.compile(
        "(?<!\\d)(?:\\d[ -]?){12,18}\\d(?!\\d)");

    public void check(String question) {
        if (question == null || EMAIL.matcher(question).find() || CARD_LIKE.matcher(question).find()) {
            throw new UnsafeInputException();
        }
    }
}
