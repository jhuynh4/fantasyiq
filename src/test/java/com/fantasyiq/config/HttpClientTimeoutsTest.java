package com.fantasyiq.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real auto-configured RestClient.Builder plus HttpClientConfig in a
 * bare application context (no database, no full app) and points it at
 * WireMock -- the same builder every vendor adapter injects, so this proves
 * the timeout actually reaches them, not just that the config class compiles.
 */
class HttpClientTimeoutsTest {

    private static final Duration READ_TIMEOUT = Duration.ofMillis(400);

    private static WireMockServer wireMock;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RestClientAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class))
            .withUserConfiguration(EnableProperties.class, HttpClientConfig.class)
            .withPropertyValues(
                    "fantasyiq.http-client.user-agent=FantasyIQ-Test/9.9",
                    "fantasyiq.http-client.connect-timeout=2s",
                    "fantasyiq.http-client.read-timeout=" + READ_TIMEOUT.toMillis() + "ms");

    @EnableConfigurationProperties(HttpClientProperties.class)
    static class EnableProperties {
    }

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(0);
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @AfterEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @Test
    void aResponseWithinTheReadTimeoutStillSucceeds() {
        wireMock.stubFor(get(urlEqualTo("/fast")).willReturn(aResponse().withStatus(200).withBody("ok")));

        contextRunner.run(context -> {
            RestClient client = context.getBean(RestClient.Builder.class).build();

            String body = client.get().uri(url("/fast")).retrieve().body(String.class);

            assertThat(body).isEqualTo("ok");
        });
    }

    @Test
    void aResponseSlowerThanTheReadTimeoutFailsQuicklyInsteadOfHanging() {
        wireMock.stubFor(get(urlEqualTo("/slow")).willReturn(aResponse().withStatus(200).withBody("late")
                .withFixedDelay(3_000)));

        contextRunner.run(context -> {
            RestClient client = context.getBean(RestClient.Builder.class).build();

            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> client.get().uri(url("/slow")).retrieve().body(String.class))
                    .isInstanceOf(ResourceAccessException.class);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

            // The server would have answered after 3s; giving up near the 400ms timeout
            // (with generous headroom for a slow CI machine) shows the timeout fired.
            assertThat(elapsed).isLessThan(Duration.ofMillis(2_000));
        });
    }

    @Test
    void everyRequestCarriesTheConfiguredUserAgentInsteadOfTheJdkDefault() {
        wireMock.stubFor(get(urlEqualTo("/who")).willReturn(aResponse().withStatus(200).withBody("ok")));

        contextRunner.run(context -> {
            RestClient client = context.getBean(RestClient.Builder.class).build();

            client.get().uri(url("/who")).retrieve().body(String.class);

            wireMock.verify(getRequestedFor(urlEqualTo("/who")).withHeader("User-Agent", equalTo("FantasyIQ-Test/9.9")));
        });
    }

    @Test
    void aBlankUserAgentIsRejectedAtStartup() {
        contextRunner.withPropertyValues("fantasyiq.http-client.user-agent= ").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("fantasyiq.http-client.user-agent must not be blank");
        });
    }

    @Test
    void aMissingOrNonPositiveTimeoutIsRejectedAtStartup() {
        contextRunner.withPropertyValues("fantasyiq.http-client.read-timeout=0s").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("fantasyiq.http-client.read-timeout must be a positive duration");
        });
    }

    private static String url(String path) {
        return "http://localhost:" + wireMock.port() + path;
    }
}
