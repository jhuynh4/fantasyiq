package com.fantasyiq.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final UnauthorizedEntryPoint unauthorizedEntryPoint;
    private final ForbiddenHandler forbiddenHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, RateLimitFilter rateLimitFilter,
                          UnauthorizedEntryPoint unauthorizedEntryPoint, ForbiddenHandler forbiddenHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.unauthorizedEntryPoint = unauthorizedEntryPoint;
        this.forbiddenHandler = forbiddenHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthorizedEntryPoint)
                        .accessDeniedHandler(forbiddenHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/actuator/health", "/actuator/info").permitAll()
                        // The endpoints that start ingestion, scoring or a backtest --
                        // they change data or take real time, so they're operator
                        // tools, not something any registered user should trigger.
                        .requestMatchers(HttpMethod.POST,
                                "/api/players/ingest",
                                "/api/games/ingest",
                                "/api/injuries/ingest",
                                "/api/player-game-stats/ingest",
                                "/api/weather/ingest",
                                "/api/odds/ingest",
                                "/api/defense-vs-position/compute",
                                "/api/recommendations/generate",
                                "/api/recommendations/backtest").hasRole("ADMIN")
                        .anyRequest().authenticated())
                // Both anchored against the same well-known Spring Security filter
                // class -- addFilterBefore/addFilterAfter only accept a class from
                // Spring Security's own registered filter ordering (FilterOrderRegistration),
                // not an arbitrary custom filter like JwtAuthenticationFilter itself
                // (using it as a reference point threw "does not have a registered
                // order" on startup). Precise relative order between these two
                // doesn't functionally matter: RateLimitFilter only acts on
                // /api/auth/**, which JwtAuthenticationFilter doesn't affect either
                // way (those endpoints are permitAll).
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
