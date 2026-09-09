package com.orqentra.notification.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Reads the caller's identity from the verified token. Nothing here comes from a request
 * body, so a caller cannot claim to be a restaurant they are not.
 */
@Component
public class CurrentUser {

    public static final String ROLE_ADMIN = "ADMIN";

    public String restaurantRef() {
        return jwt().getClaimAsString("restaurantRef");
    }

    public String email() {
        return jwt().getSubject();
    }

    public String role() {
        return jwt().getClaimAsString("role");
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role());
    }

    private Jwt jwt() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Jwt token) {
            return token;
        }
        throw new IllegalStateException("No verified JWT on the security context");
    }
}
