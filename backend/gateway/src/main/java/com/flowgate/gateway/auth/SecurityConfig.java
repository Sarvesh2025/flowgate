package com.flowgate.gateway.auth;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtAuthFilter jwtAuthFilter, ApiKeyAuthFilter apiKeyAuthFilter)
            throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(
                        c ->
                                c.configurationSource(
                                        request -> {
                                            CorsConfiguration cors = new CorsConfiguration();
                                            cors.setAllowedOriginPatterns(List.of("*"));
                                            cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                                            cors.setAllowedHeaders(List.of("*"));
                                            cors.setExposedHeaders(
                                                    List.of(
                                                            "X-Correlation-Id",
                                                            "X-RateLimit-Limit",
                                                            "X-RateLimit-Remaining",
                                                            "X-Flowgate-Route",
                                                            "X-Flowgate-Upstream",
                                                            "X-Flowgate-Cache",
                                                            "X-Flowgate-Canary"));
                                            return cors;
                                        }))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/api/v1/auth/**",
                                                "/api/v1/meta",
                                                "/api/v1/limited",
                                                "/actuator/health",
                                                "/error")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/v1/rules/**", "/api/v1/routes/**", "/api/v1/audit/**")
                                        .hasAnyRole("ADMIN", "OPERATOR", "VIEWER")
                                        .requestMatchers("/api/v1/rules/**")
                                        .hasAnyRole("ADMIN", "OPERATOR")
                                        .requestMatchers("/api/v1/keys/**")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .permitAll())
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
