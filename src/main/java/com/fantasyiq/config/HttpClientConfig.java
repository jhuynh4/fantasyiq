package com.fantasyiq.config;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;

/**
 * Without this, a vendor that accepts the connection and then never answers
 * would hang the calling thread indefinitely -- Resilience4j's retry and
 * circuit breaker only react to a call that FAILS, never to one that doesn't
 * return. With a timeout, a hung call becomes an ordinary failure
 * (ResourceAccessException) that the existing retry, circuit breaker and
 * fallback handling already know what to do with.
 *
 * Applied as a RestClientCustomizer so it reaches every adapter's injected
 * RestClient.Builder without touching any of them.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClientCustomizer vendorHttpTimeouts(HttpClientProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);
        return builder -> builder.requestFactory(requestFactory);
    }
}
