package com.fantasyiq.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate limits the unauthenticated /api/auth/** endpoints only -- per
 * SecurityConfig, those are the only endpoints permitted without a JWT, so
 * they're the ones actually exposed to unauthenticated abuse (credential
 * stuffing, registration spam). Authenticated endpoints already require a
 * valid JWT to reach at all, a stronger gate than a per-IP request count.
 *
 * In-memory only (one Bucket per client IP in a ConcurrentHashMap), not
 * Redis-backed -- this is a single-instance deployment (docs/development-plan.md's
 * Phase 7 is the first mention of running more than one instance), so a
 * distributed limiter would add real complexity (bucket4j-redis, a second
 * Redis round-trip per request) for no benefit yet. Revisit if this ever
 * runs behind a load balancer with multiple instances.
 *
 * capacity/periodSeconds come from RateLimitProperties -- application-test.yml
 * sets a much higher capacity than application.yml's real value so the IT
 * suite's own incidental /api/auth/** traffic (AuthControllerIT alone makes
 * real HTTP calls to these endpoints across its test methods) never trips
 * this in CI, same reasoning as the faster resilience4j retry/circuit-breaker
 * timings already overridden per-profile.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMITED_PATH_PREFIX = "/api/auth/";

    private final int capacity;
    private final Duration period;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, Bucket> bucketsByClientIp = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties rateLimitProperties, ObjectMapper objectMapper) {
        this.capacity = rateLimitProperties.capacity();
        this.period = Duration.ofSeconds(rateLimitProperties.periodSeconds());
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(RATE_LIMITED_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = bucketsByClientIp.computeIfAbsent(request.getRemoteAddr(), ip -> newBucket());
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Rate limit exceeded, try again later.");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/problem+json");
        objectMapper.writeValue(response.getWriter(), problem);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, period))
                .build();
    }
}
