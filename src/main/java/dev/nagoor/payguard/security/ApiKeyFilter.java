package dev.nagoor.payguard.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/** Demo API-key gate; replace with user/tenant identity and authorization in production. */
@Component
@Order(2)
public class ApiKeyFilter extends OncePerRequestFilter {
    private final byte[] expected;

    public ApiKeyFilter(@Value("${app.security.api-key}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("APP_API_KEY must be configured before startup");
        }
        expected = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp,
                                    FilterChain chain) throws ServletException, IOException {
        // Do not expose internal operational metrics to unauthenticated callers.
        if (req.getRequestURI().startsWith("/api/")
                || req.getRequestURI().equals("/actuator/prometheus")) {
            String actual = req.getHeader("X-API-Key");
            byte[] bytes = actual == null ? new byte[0] : actual.getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expected, bytes)) {
                resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid API key");
                return;
            }
        }
        chain.doFilter(req, resp);
    }
}
