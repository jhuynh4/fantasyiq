package com.fantasyiq.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressTest {

    private static final String LOAD_BALANCER = "10.0.1.25";

    private MockHttpServletRequest request(String... forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(LOAD_BALANCER);
        for (String header : forwardedFor) {
            request.addHeader("X-Forwarded-For", header);
        }
        return request;
    }

    @Test
    void usesTheConnectionAddressWhenThereAreNoProxies() {
        assertThat(ClientAddress.of(request(), 0)).isEqualTo(LOAD_BALANCER);
    }

    @Test
    void ignoresTheHeaderEntirelyWhenNoProxiesAreExpected() {
        assertThat(ClientAddress.of(request("203.0.113.9"), 0)).isEqualTo(LOAD_BALANCER);
    }

    @Test
    void readsTheViewerFromBehindCloudFrontAndTheLoadBalancer() {
        // CloudFront appended the viewer, the load balancer appended the CloudFront edge.
        assertThat(ClientAddress.of(request("198.51.100.7, 130.176.0.5"), 2)).isEqualTo("198.51.100.7");
    }

    @Test
    void readsTheClientBehindJustTheLoadBalancer() {
        assertThat(ClientAddress.of(request("198.51.100.7"), 1)).isEqualTo("198.51.100.7");
    }

    @Test
    void aCallerCannotChooseTheirOwnAddressByWritingTheHeader() {
        assertThat(ClientAddress.of(request("1.2.3.4, 198.51.100.7, 130.176.0.5"), 2)).isEqualTo("198.51.100.7");
        assertThat(ClientAddress.of(request("5.6.7.8, 198.51.100.7, 130.176.0.5"), 2)).isEqualTo("198.51.100.7");
    }

    @Test
    void joinsEntriesSplitAcrossSeveralHeaderLines() {
        assertThat(ClientAddress.of(request("1.2.3.4", "198.51.100.7, 130.176.0.5"), 2)).isEqualTo("198.51.100.7");
    }

    @Test
    void fallsBackToTheConnectionAddressWhenTheChainIsShorterThanExpected() {
        // Straight to the load balancer: only one entry, so nothing in the header can be trusted.
        assertThat(ClientAddress.of(request("198.51.100.7"), 2)).isEqualTo(LOAD_BALANCER);
        assertThat(ClientAddress.of(request(), 2)).isEqualTo(LOAD_BALANCER);
    }

    @Test
    void toleratesEmptyEntries() {
        assertThat(ClientAddress.of(request(" , 198.51.100.7 ,, 130.176.0.5 "), 2)).isEqualTo("198.51.100.7");
    }
}
