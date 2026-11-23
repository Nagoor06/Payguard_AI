package dev.nagoor.payguard.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Additional manual-review credential; review is unavailable without it. */
@Component
@Order(3)
public class ReviewerKeyFilter extends OncePerRequestFilter {
    private final byte[] expected;

    public ReviewerKeyFilter(@Value("${app.security.reviewer-key}") String reviewerKey) {
        if (reviewerKey == null || reviewerKey.isBlank()) {
            throw new IllegalStateException("REVIEWER_API_KEY must be configured before startup");
        }
        expected = reviewerKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp,
                                    FilterChain chain) throws ServletException, IOException {
        String path = req.getRequestURI();
        boolean modifyingReview = "POST".equals(req.getMethod())
            && path.matches("/api/v1/triage/[^/]+/review");
        boolean readingAudit = "GET".equals(req.getMethod())
            && path.matches("/api/v1/triage/[^/]+/events");
        if (modifyingReview || readingAudit) {
            String raw = req.getHeader("X-Reviewer-Key");
            byte[] supplied = raw == null ? new byte[0] : raw.getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expected, supplied)) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Reviewer access required");
                return;
            }
        }
        chain.doFilter(req, resp);
    }
}
