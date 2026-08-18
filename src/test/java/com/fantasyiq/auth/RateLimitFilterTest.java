package com.fantasyiq.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Constructs the filter directly with a small, explicit capacity rather
 * than going through Spring context / application-test.yml's deliberately
 * relaxed rate-limit config (see RateLimitFilter's own doc comment for why
 * that's relaxed) -- this is the actual test of the blocking behavior.
 */
class RateLimitFilterTest {

    @Test
    void allowsRequestsWithinCapacityAndRejectsBeyondIt() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new RateLimitProperties(2, 60), new ObjectMapper());

        assertThat(statusFor(filter, "/api/auth/login")).isEqualTo(200);
        assertThat(statusFor(filter, "/api/auth/login")).isEqualTo(200);
        assertThat(statusFor(filter, "/api/auth/login")).isEqualTo(429);
    }

    @Test
    void doesNotRateLimitPathsOutsideApiAuth() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new RateLimitProperties(1, 60), new ObjectMapper());

        assertThat(statusFor(filter, "/api/players/search")).isEqualTo(200);
        assertThat(statusFor(filter, "/api/players/search")).isEqualTo(200);
    }

    @Test
    void trackedSeparatelyPerClientIp() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new RateLimitProperties(1, 60), new ObjectMapper());

        assertThat(statusFor(filter, "/api/auth/login", "10.0.0.1")).isEqualTo(200);
        assertThat(statusFor(filter, "/api/auth/login", "10.0.0.2")).isEqualTo(200);
        assertThat(statusFor(filter, "/api/auth/login", "10.0.0.1")).isEqualTo(429);
    }

    private int statusFor(RateLimitFilter filter, String path) throws Exception {
        return statusFor(filter, path, "127.0.0.1");
    }

    private int statusFor(RateLimitFilter filter, String path, String remoteAddr) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remoteAddr);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }
}
