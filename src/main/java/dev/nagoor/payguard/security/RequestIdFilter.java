package dev.nagoor.payguard.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Generated rather than trusting unvalidated caller-provided tracing IDs. */
@Component
@Order(1)
public class RequestIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        resp.setHeader("X-Request-ID", requestId);
        MDC.put("requestId", requestId);
        try { chain.doFilter(req, resp); }
        finally { MDC.remove("requestId"); }
    }
}
