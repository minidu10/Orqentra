package com.orqentra.inventory.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * Verification is local: the token is checked against the shared secret in this
     * service's own configuration. No request ever calls the auth service, so auth is not
     * a synchronous dependency and cannot take every other service down with it.
     */
    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKey key = new SecretKeySpec(
                properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /** Maps the token's role claim onto a ROLE_ authority so hasRole works. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        Converter<Jwt, List<GrantedAuthority>> authorities = jwt -> {
            String role = jwt.getClaimAsString("role");
            return role == null
                    ? List.of()
                    : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        };

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities::convert);
        return converter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter converter)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Prometheus scrapes these and has no token. They are unauthenticated
                // by necessity, which is why they belong on an internal port in a
                // real deployment rather than behind the public gateway.
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated())
            // The resource server installs its own entry point for a bad token, which
            // answers with an empty body. Overriding it here keeps every auth failure in
            // the same ProblemDetail shape as the rest of the API.
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                .authenticationEntryPoint(new ProblemDetailAuthEntryPoint())
                .accessDeniedHandler(new ProblemDetailAccessDeniedHandler()))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new ProblemDetailAuthEntryPoint())
                .accessDeniedHandler(new ProblemDetailAccessDeniedHandler()));

        return http.build();
    }
}
