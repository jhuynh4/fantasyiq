package com.fantasyiq.auth;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Works out which address a request really came from when proxies sit in front of the app.
 *
 * Behind CloudFront and the load balancer, getRemoteAddr() is the load balancer's own address,
 * the same for every visitor. Each proxy appends the address it saw connecting to it to
 * X-Forwarded-For, so with two proxies the header reads "[anything the client sent], viewer,
 * cloudfront-edge". Only the entries the proxies themselves appended can be trusted, and the
 * client can write anything to the left of them, so the address is counted from the right:
 * with N proxies it is the Nth entry from the end. Taking the leftmost entry, the common
 * shortcut, would let a caller pick their own address and dodge a per-address limit.
 */
final class ClientAddress {

    static final String FORWARDED_FOR = "X-Forwarded-For";

    private ClientAddress() {
    }

    /**
     * @param proxyHops how many proxies in front of the app append to X-Forwarded-For; 0 means
     *                  none, and the header is ignored entirely
     */
    static String of(HttpServletRequest request, int proxyHops) {
        if (proxyHops <= 0) {
            return request.getRemoteAddr();
        }
        List<String> entries = forwardedFor(request);
        if (entries.size() < proxyHops) {
            // The request didn't come through the expected chain (straight to the load balancer, say),
            // so nothing in the header can be trusted as the client's address.
            return request.getRemoteAddr();
        }
        return entries.get(entries.size() - proxyHops);
    }

    private static List<String> forwardedFor(HttpServletRequest request) {
        return Collections.list(request.getHeaders(FORWARDED_FOR)).stream()
                .flatMap(header -> Arrays.stream(header.split(",")))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }
}
