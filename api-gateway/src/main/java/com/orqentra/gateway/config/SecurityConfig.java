package com.orqentra.gateway.config;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import com.orqentra.gateway.support.ProblemResponse;

import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Verified locally from the shared secret. The gateway never calls the auth service to
     * check a token, so auth is not on the request path of every other call.
     */
    @Bean
    ReactiveJwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKey key = new SecretKeySpec(
                properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * CORS lives here and only here. Configuring it in the gateway and again in a service
     * emits two Access-Control-Allow-Origin headers, which browsers reject outright, and
     * the request still succeeds server-side so nothing in the logs looks wrong.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(GatewayProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.cors().allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        cors.setExposedHeaders(List.of("X-Request-Id"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(properties.cors().maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    SecurityWebFilterChain filterChain(ServerHttpSecurity http, CorsConfigurationSource cors) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            return role == null
                    ? List.<GrantedAuthority>of()
                    : List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + role));
        });

        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(spec -> spec.configurationSource(cors))
            .authorizeExchange(exchange -> exchange
                .pathMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                .pathMatchers("/api/auth/**").permitAll()
                .pathMatchers("/actuator/health").permitAll()
                // The same rule the order service enforces. Checking it here means a
                // non-admin request is refused at the edge instead of travelling further.
                .pathMatchers("/api/admin/**").hasRole("ADMIN")
                .anyExchange().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(
                        new ReactiveJwtAuthenticationConverterAdapter(converter)))
                .authenticationEntryPoint((exchange, ex) -> ProblemResponse.write(
                        exchange, HttpStatus.UNAUTHORIZED, "Unauthorized",
                        "A valid bearer token is required"))
                .accessDeniedHandler((exchange, ex) -> ProblemResponse.write(
                        exchange, HttpStatus.FORBIDDEN, "Forbidden",
                        "This account is not allowed to perform that action")))
            .exceptionHandling(spec -> spec
                .authenticationEntryPoint((exchange, ex) -> ProblemResponse.write(
                        exchange, HttpStatus.UNAUTHORIZED, "Unauthorized",
                        "A valid bearer token is required"))
                .accessDeniedHandler((exchange, ex) -> ProblemResponse.write(
                        exchange, HttpStatus.FORBIDDEN, "Forbidden",
                        "This account is not allowed to perform that action")));

        return http.build();
    }
}
